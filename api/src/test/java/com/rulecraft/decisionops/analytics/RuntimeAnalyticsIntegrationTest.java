package com.rulecraft.decisionops.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.rulecraft.decisionops.analytics.RuntimeAnalyticsService.*;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.deployment.RuntimeDeploymentService;
import com.rulecraft.decisionops.deployment.RuntimeDeploymentService.*;
import com.rulecraft.decisionops.expressions.DecisionExpressionService;
import com.rulecraft.decisionops.expressions.DecisionExpressionService.ExpressionCommand;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.identity.domain.*;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.versions.DecisionVersionService;
import com.rulecraft.decisionops.versions.DecisionVersionService.CaptureCommand;
import com.rulecraft.decisionops.workflow.ApprovalWorkflowService;
import com.rulecraft.decisionops.workflow.ApprovalWorkflowService.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeAnalyticsIntegrationTest {
    @Autowired RuntimeAnalyticsService analytics;
    @Autowired RuntimeDeploymentService deployment;
    @Autowired ApprovalWorkflowService workflow;
    @Autowired DecisionVersionService versions;
    @Autowired DecisionExpressionService expressions;
    @Autowired DecisionGraphService graphs;
    @Autowired CatalogService catalog;
    @Autowired ProjectService projects;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    RuleCraftPrincipal admin;

    @BeforeEach void actor() {
        admin = new RuleCraftPrincipal(users.findForLogin("acme-rules", "admin@example.test").orElseThrow(), "acme-rules");
    }

    @Test void aggregatesPrivacySafeTraceMetricsAndRunsArtifactPinnedBenchmark() {
        var fixture = fixture(); var version = promote(fixture, "Analytics baseline");
        var active = deployment.deploy(admin, fixture.projectId(), fixture.serviceId(), new DeployCommand(
                UUID.randomUUID(), version.id(), workflow.workflow(admin, fixture.projectId(), fixture.serviceId()).promotion().revision()));
        var empty = analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "HOURS_24", "ALL");
        assertThat(empty.analytics().analyzedCount()).isZero();
        assertThat(empty.analytics().buckets()).hasSize(24);
        deployment.evaluate(admin, fixture.projectId(), fixture.serviceId(),
                new EvaluationCommand(Map.of("score", mapper.valueToTree(710))));
        assertCode("RC-DEPLOY-INPUT-TYPE", () -> deployment.evaluate(admin, fixture.projectId(), fixture.serviceId(),
                new EvaluationCommand(Map.of("score", mapper.valueToTree("not-a-number")))));

        var workspace = analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "HOURS_24", "ALL");
        assertThat(workspace.analytics().analyzedCount()).isEqualTo(2);
        assertThat(workspace.analytics().succeededCount()).isEqualTo(1);
        assertThat(workspace.analytics().failedCount()).isEqualTo(1);
        assertThat(workspace.analytics().successRate()).isEqualTo(50d);
        assertThat(workspace.analytics().buckets()).extracting(TimeBucket::count).contains(2);
        assertThat(workspace.analytics().channels()).singleElement().satisfies(value -> {
            assertThat(value.channel().name()).isEqualTo("SANDBOX"); assertThat(value.failedCount()).isEqualTo(1);
        });
        assertThat(workspace.analytics().errors()).extracting(ErrorStat::code).containsExactly("RC-DEPLOY-INPUT-TYPE");
        assertThat(analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "DAYS_7", "SANDBOX")
                .analytics().buckets()).hasSize(28);
        assertCode("RC-ANALYTICS-WINDOW", () -> analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "YEAR", "ALL"));
        assertCode("RC-ANALYTICS-CHANNEL", () -> analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "HOURS_24", "UNKNOWN"));

        var requestId = UUID.randomUUID();
        var command = new BenchmarkCommand(requestId, active.id(), 25, 2,
                Map.of("score", mapper.valueToTree(710)));
        var run = analytics.benchmark(admin, fixture.projectId(), fixture.serviceId(), command);
        assertThat(run.iterations()).isEqualTo(25); assertThat(run.p50Micros()).isLessThanOrEqualTo(run.p95Micros());
        assertThat(run.p95Micros()).isLessThanOrEqualTo(run.p99Micros());
        assertThat(run.throughputPerSecond()).isPositive();
        assertThat(analytics.benchmark(admin, fixture.projectId(), fixture.serviceId(), command).id()).isEqualTo(run.id());
        assertCode("RC-BENCHMARK-REQUEST-CONFLICT", () -> analytics.benchmark(admin, fixture.projectId(), fixture.serviceId(),
                new BenchmarkCommand(requestId, active.id(), 100, 2, Map.of("score", mapper.valueToTree(710)))));
        assertCode("RC-BENCHMARK-STALE", () -> analytics.benchmark(admin, fixture.projectId(), fixture.serviceId(),
                new BenchmarkCommand(UUID.randomUUID(), UUID.randomUUID(), 25, 2, Map.of("score", mapper.valueToTree(710)))));
        var after = analytics.workspace(admin, fixture.projectId(), fixture.serviceId(), "HOURS_24", "ALL");
        assertThat(after.benchmarks()).singleElement().extracting(BenchmarkView::id).isEqualTo(run.id());
        assertThat(after.analytics().analyzedCount()).isEqualTo(2);
    }

    @Test void enforcesGovernancePermissionsTenantScopeAndPublishesContract() throws Exception {
        var fixture = fixture(); var viewer = member(fixture.projectId(), "analytics-viewer", ProjectRole.VIEWER);
        var view = analytics.workspace(viewer, fixture.projectId(), fixture.serviceId(), "HOURS_24", "ALL");
        assertThat(view.canBenchmark()).isFalse();
        assertCode("RC-PROJECT-GOVERNANCE-FORBIDDEN", () -> analytics.benchmark(viewer, fixture.projectId(), fixture.serviceId(),
                new BenchmarkCommand(UUID.randomUUID(), UUID.randomUUID(), 25, 0, Map.of())));
        var account = users.save(new UserAccount(admin.tenantId(), "analytics-outsider" + UUID.randomUUID() + "@example.test",
                "Analytics Outsider", passwords.encode("CorrectHorseBattery71!"), TenantRole.MEMBER, Instant.now(clock)));
        var outsider = new RuleCraftPrincipal(account, "acme-rules");
        assertCode("RC-PROJECT-NOT-FOUND", () -> analytics.workspace(outsider, fixture.projectId(), fixture.serviceId(), "HOURS_24", "ALL"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/runtime-analytics']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/runtime-analytics/benchmarks']").exists());
    }

    private DecisionVersionService.VersionSummary promote(Fixture fixture, String label) {
        var state = versions.working(admin, fixture.projectId(), fixture.serviceId());
        var version = versions.capture(admin, fixture.projectId(), fixture.serviceId(), new CaptureCommand(UUID.randomUUID(),
                label, "Runtime analytics candidate", state.serviceVersion(), state.graphVersion(), state.fingerprint()));
        var request = workflow.create(admin, fixture.projectId(), fixture.serviceId(), new CreateCommand(UUID.randomUUID(),
                version.id(), "Publish " + label, "Analytics and benchmark review"));
        var submitted = workflow.submit(admin, fixture.projectId(), fixture.serviceId(), request.id(),
                new TransitionCommand(UUID.randomUUID(), request.version(), "Ready"));
        var approved = workflow.approve(admin, fixture.projectId(), fixture.serviceId(), request.id(),
                new TransitionCommand(UUID.randomUUID(), submitted.version(), "Approved"));
        workflow.promote(admin, fixture.projectId(), fixture.serviceId(), request.id(),
                new TransitionCommand(UUID.randomUUID(), approved.version(), "Promoted"));
        return version;
    }

    private Fixture fixture() {
        var project = projects.create(admin, key("A"), "Runtime analytics", null);
        var service = catalog.createService(admin, project.id(), new DecisionServiceCommand(key("SVC"),
                "Analytics eligibility", null, List.of(new FieldCommand("score", "Score", builtin(BuiltinType.NUMBER), true)),
                List.of(new FieldCommand("approved", "Approved", builtin(BuiltinType.BOOLEAN), true)), null));
        var graph = graphs.initialize(admin, project.id(), service.id()); var nodeId = UUID.randomUUID();
        var input = port(GraphPortDirection.INPUT, "score", BuiltinType.NUMBER);
        var output = port(GraphPortDirection.OUTPUT, "approved", BuiltinType.BOOLEAN);
        var serviceInput = graph.nodes().stream().filter(node -> node.kind() == GraphNodeKind.INPUT).findFirst().orElseThrow().ports().getFirst();
        var serviceOutput = graph.nodes().stream().filter(node -> node.kind() == GraphNodeKind.OUTPUT).findFirst().orElseThrow().ports().getFirst();
        var nodes = new ArrayList<NodeCommand>(); graph.nodes().forEach(node -> nodes.add(command(node)));
        nodes.add(new NodeCommand(nodeId, "threshold", "Approval threshold", GraphNodeKind.DECISION, null, 300, 100,
                List.of(input, output)));
        graphs.update(admin, project.id(), service.id(), new GraphCommand(graph.version(), nodes,
                List.of(edge(serviceInput.id(), input.id()), edge(output.id(), serviceOutput.id()))));
        var expression = expressions.initialize(admin, project.id(), service.id(), nodeId);
        expressions.update(admin, project.id(), service.id(), nodeId,
                new ExpressionCommand(expression.version(), "score >= 700"));
        return new Fixture(project.id(), service.id());
    }

    private RuleCraftPrincipal member(UUID projectId, String prefix, ProjectRole role) {
        var account = users.save(new UserAccount(admin.tenantId(), prefix + UUID.randomUUID() + "@example.test",
                prefix + " user", passwords.encode("CorrectHorseBattery70!"), TenantRole.MEMBER, Instant.now(clock)));
        projects.upsertMember(admin, projectId, account.getId(), role); return new RuleCraftPrincipal(account, "acme-rules");
    }
    private CatalogService.TypeRefCommand builtin(BuiltinType type) { return new CatalogService.TypeRefCommand(type, null); }
    private PortCommand port(GraphPortDirection direction, String key, BuiltinType type) { return new PortCommand(UUID.randomUUID(), direction, key, key, new DecisionGraphService.TypeRefCommand(type, null), true, 0); }
    private NodeCommand command(GraphNodeView node) { return new NodeCommand(node.id(), node.nodeKey(), node.name(), node.kind(), node.serviceFieldKey(), node.positionX(), node.positionY(), node.ports().stream().map(port -> new PortCommand(port.id(), port.direction(), port.portKey(), port.label(), port.type(), port.required(), port.position())).toList()); }
    private EdgeCommand edge(UUID source, UUID target) { return new EdgeCommand(UUID.randomUUID(), source, target); }
    private String key(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(); }
    private void assertCode(String code, Runnable action) { assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code); }
    private record Fixture(UUID projectId, UUID serviceId) {}
}

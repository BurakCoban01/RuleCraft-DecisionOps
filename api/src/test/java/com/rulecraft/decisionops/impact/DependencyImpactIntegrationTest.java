package com.rulecraft.decisionops.impact;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.expressions.DecisionExpressionService;
import com.rulecraft.decisionops.expressions.DecisionExpressionService.ExpressionCommand;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.*;
import com.rulecraft.decisionops.identity.domain.*;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.impact.DependencyImpactService.*;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.tests.DecisionTestService;
import com.rulecraft.decisionops.tests.DecisionTestService.TestCaseCommand;
import com.rulecraft.decisionops.versions.DecisionVersionService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DependencyImpactIntegrationTest {
    @Autowired DependencyImpactService impact;
    @Autowired DecisionTestService testCases;
    @Autowired DecisionExpressionService expressions;
    @Autowired DecisionVersionService versions;
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

    @Test void mapsTransitiveBlastRadiusAndCapturesImmutableStaleGuardedEvidence() throws Exception {
        var fixture = fixture();
        var workspace = impact.current(admin, fixture.projectId(), fixture.serviceId());
        assertThat(workspace.canAssess()).isTrue();
        assertThat(workspace.impact().summary().upstreamTypes()).isEqualTo(2);
        assertThat(workspace.impact().summary().graphNodes()).isEqualTo(3);
        assertThat(workspace.impact().summary().testCases()).isEqualTo(1);
        assertThat(workspace.impact().items()).extracting(ImpactItem::label)
                .contains("Applicant profile", "Postal address", "Approval threshold", "Eligible applicant");
        assertThat(workspace.impact().findings()).extracting(ImpactFinding::code)
                .contains("RC-IMPACT-NO-VERSION", "RC-IMPACT-NO-REGRESSION", "RC-IMPACT-NO-PROMOTION");

        var requestId = UUID.randomUUID();
        var command = new CaptureCommand(requestId, workspace.impact().sourceFingerprint());
        var executor = Executors.newFixedThreadPool(4);
        var ready = new CountDownLatch(4); var start = new CountDownLatch(1);
        var capturedResults = new ArrayList<AssessmentDetail>();
        try {
            var calls = new ArrayList<Future<AssessmentDetail>>();
            for (var index = 0; index < 4; index++) calls.add(executor.submit(() -> {
                ready.countDown(); start.await();
                return impact.capture(admin, fixture.projectId(), fixture.serviceId(), command);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var call : calls) capturedResults.add(call.get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertThat(capturedResults).extracting(value -> value.assessment().id()).containsOnly(capturedResults.getFirst().assessment().id());
        var captured = capturedResults.getFirst();
        assertThat(impact.capture(admin, fixture.projectId(), fixture.serviceId(), command).assessment().id())
                .isEqualTo(captured.assessment().id());
        assertThat(impact.history(admin, fixture.projectId(), fixture.serviceId())).hasSize(1);
        assertThat(impact.assessment(admin, fixture.projectId(), fixture.serviceId(), captured.assessment().id())
                .impact().items()).hasSize(workspace.impact().items().size());
        assertCode("RC-IMPACT-REQUEST-CONFLICT", () -> impact.capture(admin, fixture.projectId(), fixture.serviceId(),
                new CaptureCommand(requestId, "f".repeat(64))));

        var working = versions.working(admin, fixture.projectId(), fixture.serviceId());
        versions.capture(admin, fixture.projectId(), fixture.serviceId(), new DecisionVersionService.CaptureCommand(
                UUID.randomUUID(), "Impact candidate", "Governance identity check", working.serviceVersion(),
                working.graphVersion(), working.fingerprint()));
        var governed = impact.current(admin, fixture.projectId(), fixture.serviceId());
        assertThat(governed.impact().sourceFingerprint()).isNotEqualTo(workspace.impact().sourceFingerprint());
        assertCode("RC-IMPACT-STALE", () -> impact.capture(admin, fixture.projectId(), fixture.serviceId(),
                new CaptureCommand(UUID.randomUUID(), workspace.impact().sourceFingerprint())));

        var expression = expressions.find(admin, fixture.projectId(), fixture.serviceId(), fixture.nodeId()).orElseThrow();
        expressions.update(admin, fixture.projectId(), fixture.serviceId(), fixture.nodeId(),
                new ExpressionCommand(expression.version(), "applicant.score >= 750"));
        assertCode("RC-IMPACT-STALE", () -> impact.capture(admin, fixture.projectId(), fixture.serviceId(),
                new CaptureCommand(UUID.randomUUID(), governed.impact().sourceFingerprint())));
        assertThat(impact.assessment(admin, fixture.projectId(), fixture.serviceId(), captured.assessment().id())
                .impact().sourceFingerprint()).isEqualTo(workspace.impact().sourceFingerprint());
    }

    @Test void enforcesGovernanceRolesTenantNonDisclosureAndPublishesContract() throws Exception {
        var fixture = fixture();
        var viewer = member(fixture.projectId(), "impact-viewer", ProjectRole.VIEWER);
        var view = impact.current(viewer, fixture.projectId(), fixture.serviceId());
        assertThat(view.canAssess()).isFalse();
        assertCode("RC-PROJECT-GOVERNANCE-FORBIDDEN", () -> impact.capture(viewer, fixture.projectId(), fixture.serviceId(),
                new CaptureCommand(UUID.randomUUID(), view.impact().sourceFingerprint())));
        var outsiderAccount = users.save(new UserAccount(admin.tenantId(), "impact-outsider" + UUID.randomUUID() + "@example.test",
                "Impact Outsider", passwords.encode("CorrectHorseBattery91!"), TenantRole.MEMBER, Instant.now(clock)));
        var outsider = new RuleCraftPrincipal(outsiderAccount, "acme-rules");
        assertCode("RC-PROJECT-NOT-FOUND", () -> impact.current(outsider, fixture.projectId(), fixture.serviceId()));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/dependency-impact']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/decision-services/{serviceId}/dependency-impact/assessments']").exists());
    }

    private Fixture fixture() {
        var project = projects.create(admin, key("I"), "Dependency impact", null);
        var address = catalog.createType(admin, project.id(), new TypeCommand(key("ADDRESS"), "Postal address", null,
                TypeKind.OBJECT, null, List.of(new FieldCommand("city", "City", builtin(BuiltinType.TEXT), true)), List.of(), null));
        var applicant = catalog.createType(admin, project.id(), new TypeCommand(key("APPLICANT"), "Applicant profile", null,
                TypeKind.OBJECT, null, List.of(new FieldCommand("score", "Score", builtin(BuiltinType.NUMBER), true),
                        new FieldCommand("address", "Address", custom(address.id()), true)), List.of(), null));
        var service = catalog.createService(admin, project.id(), new DecisionServiceCommand(key("IMPACT"), "Impact eligibility", null,
                List.of(new FieldCommand("applicant", "Applicant", custom(applicant.id()), true)),
                List.of(new FieldCommand("approved", "Approved", builtin(BuiltinType.BOOLEAN), true)), null));
        var graph = graphs.initialize(admin, project.id(), service.id());
        var nodeId = UUID.randomUUID();
        var input = port(GraphPortDirection.INPUT, "applicant", applicant.id());
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
                new ExpressionCommand(expression.version(), "applicant.score >= 700"));
        var inputValue = mapper.createObjectNode(); inputValue.put("score", 710);
        inputValue.putObject("address").put("city", "Istanbul");
        testCases.create(admin, project.id(), service.id(), new TestCaseCommand(null, "eligibleApplicant", "Eligible applicant",
                "Protect the approval threshold", Map.of("applicant", inputValue), Map.of("approved", mapper.valueToTree(true))));
        return new Fixture(project.id(), service.id(), nodeId);
    }

    private RuleCraftPrincipal member(UUID projectId, String prefix, ProjectRole role) {
        var account = users.save(new UserAccount(admin.tenantId(), prefix + UUID.randomUUID() + "@example.test",
                prefix + " user", passwords.encode("CorrectHorseBattery90!"), TenantRole.MEMBER, Instant.now(clock)));
        projects.upsertMember(admin, projectId, account.getId(), role);
        return new RuleCraftPrincipal(account, "acme-rules");
    }
    private CatalogService.TypeRefCommand builtin(BuiltinType type) { return new CatalogService.TypeRefCommand(type, null); }
    private CatalogService.TypeRefCommand custom(UUID id) { return new CatalogService.TypeRefCommand(null, id); }
    private PortCommand port(GraphPortDirection direction, String key, BuiltinType type) { return new PortCommand(UUID.randomUUID(), direction, key, key, new DecisionGraphService.TypeRefCommand(type, null), true, 0); }
    private PortCommand port(GraphPortDirection direction, String key, UUID typeId) { return new PortCommand(UUID.randomUUID(), direction, key, key, new DecisionGraphService.TypeRefCommand(null, typeId), true, 0); }
    private NodeCommand command(GraphNodeView node) { return new NodeCommand(node.id(), node.nodeKey(), node.name(), node.kind(), node.serviceFieldKey(), node.positionX(), node.positionY(), node.ports().stream().map(port -> new PortCommand(port.id(), port.direction(), port.portKey(), port.label(), port.type(), port.required(), port.position())).toList()); }
    private EdgeCommand edge(UUID source, UUID target) { return new EdgeCommand(UUID.randomUUID(), source, target); }
    private String key(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(); }
    private void assertCode(String code, Runnable action) { assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code); }
    private record Fixture(UUID projectId, UUID serviceId, UUID nodeId) {}
}

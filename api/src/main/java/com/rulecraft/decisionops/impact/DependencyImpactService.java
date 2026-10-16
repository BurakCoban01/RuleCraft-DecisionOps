package com.rulecraft.decisionops.impact;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.persistence.DecisionServiceRepository;
import com.rulecraft.decisionops.deployment.domain.RuntimeDeployment;
import com.rulecraft.decisionops.deployment.persistence.*;
import com.rulecraft.decisionops.expressions.persistence.DecisionExpressionRepository;
import com.rulecraft.decisionops.graph.DecisionGraphService;
import com.rulecraft.decisionops.graph.DecisionGraphService.*;
import com.rulecraft.decisionops.graph.domain.GraphNodeKind;
import com.rulecraft.decisionops.impact.domain.*;
import com.rulecraft.decisionops.impact.persistence.ImpactAssessmentRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import com.rulecraft.decisionops.simulation.domain.MatrixRunStatus;
import com.rulecraft.decisionops.simulation.persistence.RegressionMatrixRunRepository;
import com.rulecraft.decisionops.tables.persistence.DecisionTableRepository;
import com.rulecraft.decisionops.tests.domain.TestCaseValidationStatus;
import com.rulecraft.decisionops.tests.persistence.DecisionTestCaseRepository;
import com.rulecraft.decisionops.versions.DecisionVersionService;
import com.rulecraft.decisionops.versions.domain.DecisionVersion;
import com.rulecraft.decisionops.versions.persistence.DecisionVersionRepository;
import com.rulecraft.decisionops.workflow.domain.ChangeRequestState;
import com.rulecraft.decisionops.workflow.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DependencyImpactService {
    private final ImpactAssessmentRepository assessments;
    private final DecisionServiceRepository services;
    private final CatalogService catalog;
    private final DecisionGraphService graphs;
    private final DecisionVersionService versionService;
    private final DecisionVersionRepository versions;
    private final DecisionTestCaseRepository tests;
    private final RegressionMatrixRunRepository matrices;
    private final DecisionChangeRequestRepository requests;
    private final DecisionPromotionRepository promotions;
    private final RuntimeEnvironmentRepository environments;
    private final RuntimeDeploymentRepository deployments;
    private final DecisionTableRepository tables;
    private final DecisionExpressionRepository expressions;
    private final ProjectAccessPolicy access;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DependencyImpactService(ImpactAssessmentRepository assessments, DecisionServiceRepository services,
            CatalogService catalog,
            DecisionGraphService graphs, DecisionVersionService versionService,
            DecisionVersionRepository versions, DecisionTestCaseRepository tests,
            RegressionMatrixRunRepository matrices, DecisionChangeRequestRepository requests,
            DecisionPromotionRepository promotions, RuntimeEnvironmentRepository environments,
            RuntimeDeploymentRepository deployments, DecisionTableRepository tables,
            DecisionExpressionRepository expressions, ProjectAccessPolicy access,
            AuditService audit, ObjectMapper mapper, Clock clock) {
        this.assessments = assessments; this.services = services; this.catalog = catalog; this.graphs = graphs;
        this.versionService = versionService; this.versions = versions; this.tests = tests;
        this.matrices = matrices; this.requests = requests; this.promotions = promotions;
        this.environments = environments; this.deployments = deployments; this.tables = tables;
        this.expressions = expressions; this.access = access; this.audit = audit;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ImpactWorkspace current(RuleCraftPrincipal actor, UUID projectId, UUID serviceId) {
        access.requireVisible(actor, projectId);
        return new ImpactWorkspace(build(actor, projectId, serviceId), access.canGovern(actor, projectId),
                access.roleFor(actor, projectId));
    }

    @Transactional
    public AssessmentDetail capture(RuleCraftPrincipal actor, UUID projectId, UUID serviceId,
            CaptureCommand command) {
        access.requireGovernance(actor, projectId);
        catalog.getService(actor, projectId, serviceId);
        services.lockForImpactCapture(serviceId, actor.tenantId(), projectId)
                .orElseThrow(this::serviceNotFound);
        if (command == null || command.clientRequestId() == null) {
            bad("RC-IMPACT-REQUEST-ID", "Provide a request ID so assessment retries remain idempotent.");
        }
        var retry = assessments.findByTenantIdAndServiceIdAndClientRequestId(
                actor.tenantId(), serviceId, command.clientRequestId());
        if (retry.isPresent()) {
            if (!MessageDigest.isEqual(retry.get().getSourceFingerprint().getBytes(StandardCharsets.UTF_8),
                    safe(command.expectedFingerprint()).getBytes(StandardCharsets.UTF_8))) {
                conflict("RC-IMPACT-REQUEST-CONFLICT", "This request ID already captured another impact state.");
            }
            return detail(retry.get());
        }
        var snapshot = build(actor, projectId, serviceId);
        if (command.expectedFingerprint() == null || !MessageDigest.isEqual(
                snapshot.sourceFingerprint().getBytes(StandardCharsets.UTF_8),
                command.expectedFingerprint().getBytes(StandardCharsets.UTF_8))) {
            conflict("RC-IMPACT-STALE", "The decision dependency state changed. Refresh before capturing evidence.");
        }
        if (assessments.countByTenantIdAndServiceId(actor.tenantId(), serviceId) >= 1000) {
            bad("RC-IMPACT-LIMIT", "A decision service can retain at most 1,000 impact assessments.");
        }
        var evidence = json(snapshot);
        var value = new ImpactAssessment(actor.tenantId(), projectId, serviceId, command.clientRequestId(),
                snapshot.sourceFingerprint(), hash(evidence), snapshot.riskLevel(),
                snapshot.summary().totalDependencies(), snapshot.findings().size(),
                snapshot.summary().blockers(), evidence, actor.userId(), actor.displayName(), Instant.now(clock));
        assessments.saveAndFlush(value);
        audit.record(actor.tenantId(), actor.userId(), "IMPACT_ASSESSMENT_CAPTURED", "IMPACT_ASSESSMENT",
                value.getId(), Map.of("serviceId", serviceId, "riskLevel", value.getRiskLevel(),
                        "dependencyCount", value.getDependencyCount(), "blockerCount", value.getBlockerCount(),
                        "sourceFingerprint", value.getSourceFingerprint()));
        return detail(value);
    }

    @Transactional(readOnly = true)
    public List<AssessmentSummary> history(RuleCraftPrincipal actor, UUID projectId, UUID serviceId) {
        access.requireVisible(actor, projectId); catalog.getService(actor, projectId, serviceId);
        return assessments.findTop100ByTenantIdAndProjectIdAndServiceIdOrderByCapturedAtDesc(
                actor.tenantId(), projectId, serviceId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDetail assessment(RuleCraftPrincipal actor, UUID projectId, UUID serviceId, UUID assessmentId) {
        access.requireVisible(actor, projectId); catalog.getService(actor, projectId, serviceId);
        return detail(assessments.findByIdAndTenantIdAndProjectIdAndServiceId(
                assessmentId, actor.tenantId(), projectId, serviceId).orElseThrow(this::notFound));
    }

    private ImpactSnapshot build(RuleCraftPrincipal actor, UUID projectId, UUID serviceId) {
        var service = catalog.getService(actor, projectId, serviceId);
        var graph = graphs.get(actor, projectId, serviceId);
        var working = versionService.working(actor, projectId, serviceId);
        var typeViews = catalog.listTypes(actor, projectId);
        var typeById = typeViews.stream().collect(Collectors.toMap(TypeView::id, Function.identity()));
        var testCases = tests.findAllByDecisionServiceIdAndTenantIdAndProjectIdOrderByNameAsc(
                serviceId, actor.tenantId(), projectId);
        var versionValues = versions.findTop100ByDecisionServiceIdAndTenantIdAndProjectIdOrderByVersionNumberDesc(
                serviceId, actor.tenantId(), projectId);
        var requestValues = requests.findTop100ByTenantIdAndProjectIdAndServiceIdOrderByRequestNumberDesc(
                actor.tenantId(), projectId, serviceId);
        var matrixValues = matrices.findTop20ByDecisionServiceIdAndTenantIdAndProjectIdOrderByStartedAtDesc(
                serviceId, actor.tenantId(), projectId);
        var promotion = promotions.findByServiceIdAndTenantIdAndProjectId(serviceId, actor.tenantId(), projectId);
        var environment = environments.findByServiceIdAndTenantIdAndProjectId(serviceId, actor.tenantId(), projectId);
        RuntimeDeployment active = environment.filter(value -> value.getActiveDeploymentId() != null)
                .flatMap(value -> deployments.findByIdAndServiceIdAndTenantIdAndProjectId(
                        value.getActiveDeploymentId(), serviceId, actor.tenantId(), projectId)).orElse(null);

        var items = new ArrayList<ImpactItem>();
        var findings = new ArrayList<ImpactFinding>();
        var referencedTypes = typeClosure(service, graph, typeById);
        referencedTypes.stream().map(typeById::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(TypeView::name))
                .forEach(type -> items.add(item("UPSTREAM", "TYPE", type.id(), type.name(),
                        type.typeKey() + " · " + type.kind(), "REFERENCED", ImpactRisk.LOW)));

        var nodeByPort = new HashMap<UUID, GraphNodeView>();
        graph.nodes().forEach(node -> node.ports().forEach(port -> nodeByPort.put(port.id(), node)));
        var tableByNode = tables.findAllByGraphIdAndTenantIdOrderByUpdatedAtDesc(graph.id(), actor.tenantId())
                .stream().collect(Collectors.toMap(value -> value.getNodeId(), Function.identity()));
        var expressionByNode = expressions.findAllByGraphIdAndTenantIdOrderByUpdatedAtDesc(graph.id(), actor.tenantId())
                .stream().collect(Collectors.toMap(value -> value.getNodeId(), Function.identity()));
        for (var node : graph.nodes()) {
            var inbound = graph.edges().stream().filter(edge -> Objects.equals(nodeByPort.get(edge.targetPortId()), node)).count();
            var outbound = graph.edges().stream().filter(edge -> Objects.equals(nodeByPort.get(edge.sourcePortId()), node)).count();
            var implementation = node.kind() == GraphNodeKind.DECISION
                    ? tableByNode.containsKey(node.id()) ? "TABLE · " + tableByNode.get(node.id()).getHitPolicy()
                    : expressionByNode.containsKey(node.id()) ? "EXPRESSION · " + expressionByNode.get(node.id()).getValidationStatus()
                    : "NO IMPLEMENTATION" : "CONTRACT";
            var nodeRisk = node.kind() == GraphNodeKind.DECISION && implementation.equals("NO IMPLEMENTATION")
                    ? ImpactRisk.BLOCKED : ImpactRisk.LOW;
            items.add(item("MODEL", "GRAPH_NODE", node.id(), node.name(),
                    node.nodeKey() + " · " + inbound + " upstream / " + outbound + " downstream · " + implementation,
                    node.kind().name(), nodeRisk));
            if (nodeRisk == ImpactRisk.BLOCKED) findings.add(finding("RC-IMPACT-NODE-UNIMPLEMENTED", nodeRisk,
                    "Decision has no implementation", node.name() + " cannot produce governed results.",
                    "Author a table or expression before review.", node.id()));
        }

        testCases.forEach(test -> items.add(item("ASSURANCE", "TEST_CASE", test.getId(), test.getName(),
                test.getTestKey() + " · version " + test.getVersion(), test.getValidationStatus().name(),
                test.getValidationStatus() == TestCaseValidationStatus.VALID ? ImpactRisk.LOW : ImpactRisk.HIGH)));
        versionValues.forEach(version -> items.add(item("GOVERNANCE", "VERSION", version.getId(),
                "V" + version.getVersionNumber() + " · " + version.getLabel(), version.getFingerprint(),
                version.getValidationStatus().name(), version.getFingerprint().equals(working.fingerprint())
                        ? ImpactRisk.LOW : ImpactRisk.MEDIUM)));
        requestValues.forEach(request -> items.add(item("GOVERNANCE", "CHANGE_REQUEST", request.getId(),
                "CR-" + request.getRequestNumber() + " · " + request.getTitle(),
                "Candidate " + shortId(request.getDecisionVersionId()), request.getState().name(),
                open(request.getState()) ? ImpactRisk.MEDIUM : ImpactRisk.LOW)));
        promotion.ifPresent(value -> items.add(item("GOVERNANCE", "PROMOTION", value.getRequestId(),
                "Governed baseline", "Version " + shortId(value.getDecisionVersionId()) + " · " + value.getPromotedByName(),
                "PROMOTED", ImpactRisk.LOW)));
        if (active != null) items.add(item("RUNTIME", "DEPLOYMENT", active.getId(),
                "Deployment #" + active.getSequenceNumber(), active.getArtifactFingerprint(), active.getState().name(),
                ImpactRisk.LOW));
        matrixValues.forEach(run -> items.add(item("ASSURANCE", "REGRESSION_RUN", run.getId(),
                "Regression " + shortId(run.getId()), run.getSelectedCount() + " scenarios · " + run.getFailedCount()
                        + " failed · " + run.getErrorCount() + " errors", run.getStatus().name(),
                run.getFailedCount() + run.getErrorCount() > 0 || run.getStatus() == MatrixRunStatus.INTERRUPTED
                        ? ImpactRisk.HIGH : ImpactRisk.LOW)));

        if (!"VALID".equals(working.validationStatus().name()) || !"VALID".equals(graph.validationStatus().name())) {
            findings.add(finding("RC-IMPACT-WORKING-INVALID", ImpactRisk.BLOCKED, "Working decision is invalid",
                    "The current contract, graph, or implementation does not pass structural validation.",
                    "Resolve graph and implementation issues before governance review.", serviceId));
        }
        var invalidTests = testCases.stream().filter(value -> value.getValidationStatus() != TestCaseValidationStatus.VALID).count();
        if (invalidTests > 0) findings.add(finding("RC-IMPACT-TEST-INVALID", ImpactRisk.HIGH,
                invalidTests + " scenario" + (invalidTests == 1 ? " is" : "s are") + " invalid",
                "Invalid scenarios cannot provide reliable regression evidence.",
                "Repair or remove stale scenario inputs and expected outputs.", serviceId));
        if (testCases.isEmpty()) findings.add(finding("RC-IMPACT-NO-TESTS", ImpactRisk.MEDIUM,
                "No persisted scenarios", "Behavioral changes have no service-level regression coverage.",
                "Add representative test cases before promotion.", serviceId));
        var latest = versionValues.stream().findFirst();
        if (latest.isEmpty()) findings.add(finding("RC-IMPACT-NO-VERSION", ImpactRisk.MEDIUM,
                "No immutable version", "The working dependency state has not been captured.",
                "Capture a decision version before opening review.", serviceId));
        else if (!latest.get().getFingerprint().equals(working.fingerprint())) findings.add(finding(
                "RC-IMPACT-UNCAPTURED", ImpactRisk.MEDIUM, "Working copy differs from latest version",
                "Dependency evidence changed after V" + latest.get().getVersionNumber() + ".",
                "Capture the working decision and reassess impact.", latest.get().getId()));
        if (promotion.isEmpty()) findings.add(finding("RC-IMPACT-NO-PROMOTION", ImpactRisk.MEDIUM,
                "No governed baseline", "No approved version is promoted for this service.",
                "Complete change review and promotion when the candidate is ready.", serviceId));
        else {
            var promoted = versionValues.stream().filter(value -> value.getId().equals(promotion.get().getDecisionVersionId())).findFirst()
                    .or(() -> versions.findByIdAndDecisionServiceIdAndTenantIdAndProjectId(
                            promotion.get().getDecisionVersionId(), serviceId, actor.tenantId(), projectId));
            if (promoted.isPresent() && !promoted.get().getFingerprint().equals(working.fingerprint())) findings.add(finding(
                    "RC-IMPACT-PROMOTION-DRIFT", ImpactRisk.MEDIUM, "Working copy differs from governed baseline",
                    "The current model is not the promoted artifact.", "Review the semantic diff before replacing the baseline.",
                    promotion.get().getDecisionVersionId()));
            if (active != null && promoted.isPresent()
                    && !active.getArtifactFingerprint().equals(promoted.get().getFingerprint())) findings.add(finding(
                    "RC-IMPACT-RUNTIME-DRIFT", ImpactRisk.HIGH, "Runtime differs from governed baseline",
                    "The active deployment fingerprint does not match the promoted version.",
                    "Deploy the promoted artifact or document the rollback state.", active.getId()));
        }
        if (matrixValues.isEmpty()) findings.add(finding("RC-IMPACT-NO-REGRESSION", ImpactRisk.MEDIUM,
                "No regression evidence", "No bounded matrix run has been recorded for this service.",
                "Run the persisted scenario matrix before promotion.", serviceId));
        else {
            var run = matrixValues.getFirst();
            if (run.getStatus() != MatrixRunStatus.COMPLETED || run.getFailedCount() > 0 || run.getErrorCount() > 0) {
                findings.add(finding("RC-IMPACT-REGRESSION-RISK", ImpactRisk.HIGH, "Latest regression is not clean",
                        "The newest run is " + run.getStatus() + " with " + run.getFailedCount() + " failures and "
                                + run.getErrorCount() + " errors.",
                        "Complete a clean regression matrix before promotion.", run.getId()));
            }
        }
        if (active == null) findings.add(finding("RC-IMPACT-NO-RUNTIME", ImpactRisk.MEDIUM,
                "No active sandbox artifact", "Runtime consumers have no active local deployment.",
                "Deploy the promoted baseline when runtime verification is required.", serviceId));

        var risk = findings.stream().map(ImpactFinding::severity).max(Comparator.comparingInt(this::rank)).orElse(ImpactRisk.LOW);
        var blockers = (int) findings.stream().filter(value -> value.severity() == ImpactRisk.BLOCKED
                || value.severity() == ImpactRisk.HIGH).count();
        var warnings = findings.size() - blockers;
        var openRequests = (int) requestValues.stream().filter(value -> open(value.getState())).count();
        var summary = new ImpactSummary(referencedTypes.size(), graph.nodes().size(), testCases.size(),
                versionValues.size(), openRequests, active == null ? 0 : 1, matrixValues.size(), items.size(), blockers, warnings);
        var sourceFingerprint = hash(json(new ImpactIdentity(working.fingerprint(),
                working.validationStatus().name(), graph.validationStatus().name(),
                List.copyOf(findings), List.copyOf(items))));
        return new ImpactSnapshot(service.id(), service.serviceKey(), service.name(), Instant.now(clock),
                sourceFingerprint, working.validationStatus().name(), risk, blockers == 0,
                summary, List.copyOf(findings), List.copyOf(items));
    }

    private Set<UUID> typeClosure(DecisionServiceView service, GraphView graph, Map<UUID, TypeView> types) {
        var result = new LinkedHashSet<UUID>();
        var queue = new ArrayDeque<UUID>();
        service.inputs().forEach(value -> add(value.type(), result, queue));
        service.outputs().forEach(value -> add(value.type(), result, queue));
        graph.nodes().forEach(node -> node.ports().forEach(port -> {
            if (port.type().customTypeId() != null && result.add(port.type().customTypeId())) queue.add(port.type().customTypeId());
        }));
        while (!queue.isEmpty()) {
            var type = types.get(queue.remove());
            if (type == null) continue;
            add(type.itemType(), result, queue);
            type.fields().forEach(value -> add(value.type(), result, queue));
        }
        return result;
    }

    private void add(TypeRefView ref, Set<UUID> result, Queue<UUID> queue) {
        if (ref != null && ref.customTypeId() != null && result.add(ref.customTypeId())) queue.add(ref.customTypeId());
    }
    private ImpactItem item(String category, String kind, UUID id, String label, String detail, String status, ImpactRisk risk) {
        return new ImpactItem(category, kind, id, label, detail, status, risk);
    }
    private ImpactFinding finding(String code, ImpactRisk severity, String title, String detail, String action, UUID id) {
        return new ImpactFinding(code, severity, title, detail, action, id);
    }
    private boolean open(ChangeRequestState state) {
        return state == ChangeRequestState.DRAFT || state == ChangeRequestState.SUBMITTED
                || state == ChangeRequestState.CHANGES_REQUESTED || state == ChangeRequestState.APPROVED;
    }
    private int rank(ImpactRisk value) { return switch (value) { case LOW -> 0; case MEDIUM -> 1; case HIGH -> 2; case BLOCKED -> 3; }; }
    private String shortId(UUID id) { return id.toString().substring(0, 8); }
    private AssessmentSummary summary(ImpactAssessment value) {
        return new AssessmentSummary(value.getId(), value.getSourceFingerprint(), value.getSnapshotFingerprint(),
                value.getRiskLevel(), value.getDependencyCount(), value.getFindingCount(), value.getBlockerCount(),
                value.getCapturedByName(), value.getCapturedAt());
    }
    private AssessmentDetail detail(ImpactAssessment value) {
        try { return new AssessmentDetail(summary(value), mapper.readValue(value.getEvidenceJson(), ImpactSnapshot.class)); }
        catch (JacksonException failure) { throw new IllegalStateException("Persisted impact evidence is invalid.", failure); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException failure) { throw new IllegalStateException("Impact evidence could not be serialized.", failure); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String safe(String value) { return value == null ? "" : value; }
    private void bad(String code, String detail) { throw new DomainProblemException(HttpStatus.BAD_REQUEST, code, detail); }
    private void conflict(String code, String detail) { throw new DomainProblemException(HttpStatus.CONFLICT, code, detail); }
    private DomainProblemException notFound() { return new DomainProblemException(HttpStatus.NOT_FOUND,
            "RC-IMPACT-NOT-FOUND", "Impact assessment not found."); }
    private DomainProblemException serviceNotFound() { return new DomainProblemException(HttpStatus.NOT_FOUND,
            "RC-CATALOG-SERVICE-NOT-FOUND", "Decision service not found."); }

    public record CaptureCommand(UUID clientRequestId, String expectedFingerprint) {}
    public record ImpactWorkspace(ImpactSnapshot impact, boolean canAssess, String accessRole) {}
    public record ImpactSnapshot(UUID serviceId, String serviceKey, String serviceName, Instant generatedAt,
            String sourceFingerprint, String workingValidationStatus, ImpactRisk riskLevel, boolean readyForReview,
            ImpactSummary summary, List<ImpactFinding> findings, List<ImpactItem> items) {}
    public record ImpactSummary(int upstreamTypes, int graphNodes, int testCases, int versions,
            int openRequests, int activeDeployments, int regressionRuns, int totalDependencies,
            int blockers, int warnings) {}
    public record ImpactFinding(String code, ImpactRisk severity, String title, String detail,
            String action, UUID referenceId) {}
    public record ImpactItem(String category, String kind, UUID referenceId, String label,
            String detail, String status, ImpactRisk risk) {}
    public record AssessmentSummary(UUID id, String sourceFingerprint, String snapshotFingerprint,
            ImpactRisk riskLevel, int dependencyCount, int findingCount, int blockerCount,
            String capturedByName, Instant capturedAt) {}
    public record AssessmentDetail(AssessmentSummary assessment, ImpactSnapshot impact) {}
    private record ImpactIdentity(String workingFingerprint, String workingValidationStatus,
            String graphValidationStatus, List<ImpactFinding> findings, List<ImpactItem> items) {}
}

package com.rulecraft.decisionops.explainability;

import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.persistence.DecisionServiceRepository;
import com.rulecraft.decisionops.decisionapi.security.DecisionApiPrincipal;
import com.rulecraft.decisionops.deployment.CapturedDecisionRuntime;
import com.rulecraft.decisionops.explainability.domain.*;
import com.rulecraft.decisionops.explainability.persistence.DecisionTraceRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;

@Service
public class ExplainabilityService {
    private final DecisionTraceRepository traces;
    private final DecisionTraceStore store;
    private final ProjectAccessPolicy access;
    private final CatalogService catalog;
    private final DecisionServiceRepository services;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ExplainabilityService(DecisionTraceRepository traces, DecisionTraceStore store,
            ProjectAccessPolicy access, CatalogService catalog, DecisionServiceRepository services,
            ObjectMapper mapper, Clock clock) {
        this.traces = traces;
        this.store = store;
        this.access = access;
        this.catalog = catalog;
        this.services = services;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Capture success(TraceContext context, Map<String, JsonNode> inputs,
            CapturedDecisionRuntime.Evaluation evaluation, long durationMicros) {
        return new Capture(context, inputs, evaluation, null, null, durationMicros);
    }

    public Capture failure(TraceContext context, Map<String, JsonNode> inputs,
            String errorCode, String errorDetail, long durationMicros) {
        return new Capture(context, inputs, null, errorCode, safeDetail(errorDetail), durationMicros);
    }

    public void record(Capture capture) { recordAll(List.of(capture)); }

    public void recordAll(List<Capture> captures) {
        if (captures == null || captures.isEmpty()) return;
        var values = captures.stream().map(this::entity).toList();
        try {
            store.persist(values);
        } catch (DataIntegrityViolationException race) {
            for (var value : values) {
                var existing = store.load(value.getEvaluationId());
                if (existing.isPresent()) {
                    if (!sameIdentity(existing.get(), value)) throw race;
                } else {
                    try { store.persist(List.of(value)); }
                    catch (DataIntegrityViolationException duplicate) {
                        var winner = store.load(value.getEvaluationId()).orElseThrow(() -> duplicate);
                        if (!sameIdentity(winner, value)) throw duplicate;
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TraceSummary> history(RuleCraftPrincipal actor, UUID projectId, UUID serviceId,
            String channel, String status, String query) {
        access.requireVisible(actor, projectId);
        catalog.getService(actor, projectId, serviceId);
        var expectedChannel = parseChannel(channel);
        var expectedStatus = parseStatus(status);
        var search = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (search.length() > 120) bad("RC-TRACE-QUERY-LIMIT", "Trace search contains at most 120 characters.");
        return traces.findTop100ByTenantIdAndProjectIdAndServiceIdOrderByCreatedAtDesc(
                actor.tenantId(), projectId, serviceId).stream()
                .filter(value -> expectedChannel == null || value.getChannel() == expectedChannel)
                .filter(value -> expectedStatus == null || value.getStatus() == expectedStatus)
                .filter(value -> search.isBlank() || searchable(value).contains(search))
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public TraceDetail detail(RuleCraftPrincipal actor, UUID projectId, UUID serviceId, UUID evaluationId) {
        access.requireVisible(actor, projectId);
        catalog.getService(actor, projectId, serviceId);
        return detail(traces.findScoped(evaluationId, actor.tenantId(), projectId, serviceId)
                .orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public TraceDetail publicDetail(DecisionApiPrincipal principal, String serviceKey, UUID evaluationId) {
        var service = services.findByIdAndTenantIdAndProjectId(principal.serviceId(), principal.tenantId(),
                principal.projectId()).orElseThrow(this::notFound);
        if (serviceKey == null || !service.getServiceKey().equalsIgnoreCase(serviceKey)) throw notFound();
        return detail(traces.findForCredential(evaluationId, principal.tenantId(), principal.serviceId(),
                principal.credentialId()).orElseThrow(this::notFound));
    }

    private DecisionTrace entity(Capture capture) {
        var context = capture.context();
        var inputs = capture.inputs() == null ? Map.<String, JsonNode>of() : capture.inputs();
        var evaluation = capture.evaluation();
        var outputs = evaluation == null ? Map.<String, JsonNode>of() : evaluation.outputs();
        var steps = new ArrayList<DecisionTraceStep>();
        if (evaluation != null) {
            for (int index = 0; index < evaluation.decisions().size(); index++) {
                var step = evaluation.decisions().get(index);
                steps.add(new DecisionTraceStep(index, bounded(step.nodeKey(), 120),
                        bounded(step.nodeName(), 160), step.implementation(), step.hitPolicy(),
                        boundedNullable(step.matchedRuleId(), 80), json(sorted(step.inputKeys())),
                        json(redact(step.value()))));
            }
        }
        var status = evaluation == null ? TraceStatus.FAILED : TraceStatus.SUCCEEDED;
        return new DecisionTrace(context.evaluationId(), context.tenantId(), context.projectId(),
                context.serviceId(), context.deploymentId(), context.decisionVersionId(),
                context.credentialId(), context.actorUserId(), bounded(context.subjectLabel(), 160),
                context.clientRequestId(), boundedNullable(context.correlationId(), 80), context.channel(),
                status, context.artifactFingerprint(), fingerprint(inputs),
                evaluation == null ? null : fingerprint(outputs), json(sorted(inputs.keySet())),
                json(sorted(outputs.keySet())), capture.errorCode(), capture.errorDetail(),
                Math.max(0, capture.durationMicros()), Instant.now(clock), steps);
    }

    private TraceSummary summary(DecisionTrace value) {
        return new TraceSummary(value.getEvaluationId(), value.getDeploymentId(), value.getDecisionVersionId(),
                value.getClientRequestId(), value.getCorrelationId(), value.getChannel(), value.getStatus(),
                value.getArtifactFingerprint(), value.getSubjectLabel(), value.getErrorCode(),
                value.getDurationMicros(), value.getStepCount(), value.getCreatedAt());
    }

    private TraceDetail detail(DecisionTrace value) {
        var steps = value.getSteps().stream().map(step -> new StepView(step.getPosition(), step.getNodeKey(),
                step.getNodeName(), step.getImplementation(), step.getHitPolicy(), step.getMatchedRuleId(),
                strings(step.getInputKeysJson()), read(step.getResultJson()))).toList();
        return new TraceDetail(summary(value), value.getInputFingerprint(), value.getOutputFingerprint(),
                strings(value.getInputKeysJson()), strings(value.getOutputKeysJson()), value.getErrorDetail(), steps);
    }

    private JsonNode redact(JsonNode value) {
        if (value == null || value.isNull()) return NullNode.getInstance();
        if (value.isBoolean()) return BooleanNode.valueOf(value.asBoolean());
        var result = mapper.createObjectNode();
        result.put("redacted", true);
        result.put("type", value.getNodeType().name());
        result.put("fingerprint", fingerprint(value));
        if (value.isTextual()) result.put("length", value.asString().length());
        else if (value.isArray() || value.isObject()) result.put("size", value.size());
        return result;
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(sort(mapper.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull()) return NullNode.getInstance();
        if (value.isObject()) {
            var result = mapper.createObjectNode();
            value.propertyNames().stream().sorted().forEach(key -> result.set(key, sort(value.get(key))));
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.values().forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }

    private List<String> strings(String value) {
        try { return mapper.readValue(value, mapper.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch (JacksonException failure) { throw new IllegalStateException("Persisted trace keys are invalid.", failure); }
    }
    private JsonNode read(String value) {
        try { return mapper.readTree(value); }
        catch (JacksonException failure) { throw new IllegalStateException("Persisted trace result is invalid.", failure); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException failure) { throw new IllegalStateException("Trace evidence could not be serialized.", failure); }
    }
    private List<String> sorted(Collection<String> values) { return values.stream().sorted().toList(); }
    private boolean sameIdentity(DecisionTrace left, DecisionTrace right) {
        return left.getTenantId().equals(right.getTenantId()) && left.getServiceId().equals(right.getServiceId())
                && left.getDeploymentId().equals(right.getDeploymentId())
                && left.getInputFingerprint().equals(right.getInputFingerprint());
    }
    private String searchable(DecisionTrace value) {
        return (value.getEvaluationId() + " " + value.getClientRequestId() + " " + value.getCorrelationId()
                + " " + value.getSubjectLabel() + " " + value.getArtifactFingerprint()).toLowerCase(Locale.ROOT);
    }
    private TraceChannel parseChannel(String value) {
        if (value == null || value.isBlank()) return null;
        try { return TraceChannel.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { bad("RC-TRACE-CHANNEL", "Choose a supported trace channel."); return null; }
    }
    private TraceStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return TraceStatus.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { bad("RC-TRACE-STATUS", "Choose a supported trace status."); return null; }
    }
    private String safeDetail(String value) { return value == null ? "Evaluation failed." : bounded(value, 600); }
    private String bounded(String value, int limit) {
        var normalized = value == null ? "" : value.strip();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }
    private String boundedNullable(String value, int limit) { return value == null ? null : bounded(value, limit); }
    private void bad(String code, String detail) { throw new DomainProblemException(HttpStatus.BAD_REQUEST, code, detail); }
    private DomainProblemException notFound() { return new DomainProblemException(HttpStatus.NOT_FOUND, "RC-TRACE-NOT-FOUND", "Decision trace not found."); }

    public record TraceContext(UUID evaluationId, UUID tenantId, UUID projectId, UUID serviceId,
            UUID deploymentId, UUID decisionVersionId, UUID credentialId, UUID actorUserId,
            String subjectLabel, UUID clientRequestId, String correlationId, TraceChannel channel,
            String artifactFingerprint) {}
    public record Capture(TraceContext context, Map<String, JsonNode> inputs,
            CapturedDecisionRuntime.Evaluation evaluation, String errorCode, String errorDetail,
            long durationMicros) {}
    public record TraceSummary(UUID evaluationId, UUID deploymentId, UUID decisionVersionId,
            UUID requestId, String correlationId, TraceChannel channel, TraceStatus status,
            String artifactFingerprint, String subjectLabel, String errorCode, long durationMicros,
            int stepCount, Instant createdAt) {}
    public record StepView(int position, String nodeKey, String nodeName, String implementation,
            String hitPolicy, String matchedRuleId, List<String> inputKeys, JsonNode result) {}
    public record TraceDetail(TraceSummary trace, String inputFingerprint, String outputFingerprint,
            List<String> inputKeys, List<String> outputKeys, String errorDetail, List<StepView> steps) {}
}

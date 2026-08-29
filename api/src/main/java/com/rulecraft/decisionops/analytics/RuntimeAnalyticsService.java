package com.rulecraft.decisionops.analytics;

import com.rulecraft.decisionops.analytics.domain.RuntimeBenchmarkRun;
import com.rulecraft.decisionops.analytics.persistence.RuntimeBenchmarkRunRepository;
import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.catalog.CatalogService;
import com.rulecraft.decisionops.catalog.persistence.DecisionServiceRepository;
import com.rulecraft.decisionops.deployment.CapturedDecisionRuntime;
import com.rulecraft.decisionops.deployment.domain.RuntimeDeploymentState;
import com.rulecraft.decisionops.deployment.persistence.*;
import com.rulecraft.decisionops.explainability.domain.*;
import com.rulecraft.decisionops.explainability.persistence.DecisionTraceRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectAccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;
import tools.jackson.databind.node.NullNode;

@Service
public class RuntimeAnalyticsService {
    private static final int TRACE_LIMIT = 10_000;
    private final DecisionTraceRepository traces;
    private final RuntimeBenchmarkRunRepository benchmarks;
    private final RuntimeEnvironmentRepository environments;
    private final RuntimeDeploymentRepository deployments;
    private final DecisionServiceRepository services;
    private final CatalogService catalog;
    private final ProjectAccessPolicy access;
    private final CapturedDecisionRuntime runtime;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public RuntimeAnalyticsService(DecisionTraceRepository traces, RuntimeBenchmarkRunRepository benchmarks,
            RuntimeEnvironmentRepository environments, RuntimeDeploymentRepository deployments,
            DecisionServiceRepository services, CatalogService catalog, ProjectAccessPolicy access,
            CapturedDecisionRuntime runtime, AuditService audit, ObjectMapper mapper, Clock clock) {
        this.traces = traces; this.benchmarks = benchmarks; this.environments = environments;
        this.deployments = deployments; this.services = services; this.catalog = catalog; this.access = access;
        this.runtime = runtime; this.audit = audit; this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsWorkspace workspace(RuleCraftPrincipal actor, UUID projectId, UUID serviceId,
            String requestedWindow, String requestedChannel) {
        access.requireVisible(actor, projectId);
        var service = catalog.getService(actor, projectId, serviceId);
        var window = AnalyticsWindow.parse(requestedWindow);
        var channel = channel(requestedChannel);
        var now = Instant.now(clock); var since = now.minus(window.duration);
        var totalAvailable = traces.countAnalytics(actor.tenantId(), projectId, serviceId, since, now, channel);
        var values = traces.findAnalytics(actor.tenantId(), projectId, serviceId, since, now, channel,
                PageRequest.of(0, TRACE_LIMIT));
        var analytics = summarize(window, channel, since, now, totalAvailable, values);
        var history = benchmarks.findTop50ByTenantIdAndProjectIdAndServiceIdOrderByCapturedAtDesc(
                actor.tenantId(), projectId, serviceId).stream().map(this::view).toList();
        return new AnalyticsWorkspace(service.serviceKey(), service.name(), analytics, active(actor, projectId, serviceId),
                history, access.canGovern(actor, projectId), access.roleFor(actor, projectId));
    }

    @Transactional
    public BenchmarkView benchmark(RuleCraftPrincipal actor, UUID projectId, UUID serviceId, BenchmarkCommand command) {
        access.requireGovernance(actor, projectId); catalog.getService(actor, projectId, serviceId);
        if (command == null || command.clientRequestId() == null) bad("RC-BENCHMARK-REQUEST-ID",
                "Provide a request ID so benchmark retries remain idempotent.");
        if (command.iterations() == null || command.iterations() < 10 || command.iterations() > 500) bad(
                "RC-BENCHMARK-ITERATIONS", "Choose between 10 and 500 measured iterations.");
        if (command.warmupIterations() == null || command.warmupIterations() < 0 || command.warmupIterations() > 50) bad(
                "RC-BENCHMARK-WARMUP", "Choose between 0 and 50 warmup iterations.");
        var inputs = command.inputs() == null ? Map.<String, JsonNode>of() : command.inputs();
        var requestFingerprint = fingerprint(Map.of("iterations", command.iterations(),
                "warmupIterations", command.warmupIterations(), "expectedDeploymentId",
                command.expectedDeploymentId() == null ? "" : command.expectedDeploymentId(), "inputs", inputs));
        var retry = benchmarks.findByTenantIdAndServiceIdAndClientRequestId(
                actor.tenantId(), serviceId, command.clientRequestId());
        if (retry.isPresent()) return exactRetry(retry.get(), requestFingerprint);
        services.lockForBenchmark(serviceId, actor.tenantId(), projectId).orElseThrow(this::serviceNotFound);
        retry = benchmarks.findByTenantIdAndServiceIdAndClientRequestId(
                actor.tenantId(), serviceId, command.clientRequestId());
        if (retry.isPresent()) return exactRetry(retry.get(), requestFingerprint);
        if (benchmarks.countByTenantIdAndServiceId(actor.tenantId(), serviceId) >= 1_000) bad(
                "RC-BENCHMARK-LIMIT", "A decision service can retain at most 1,000 benchmark runs.");
        var deployment = requireActive(actor, projectId, serviceId);
        if (command.expectedDeploymentId() == null || !command.expectedDeploymentId().equals(deployment.getId())) {
            throw conflict("RC-BENCHMARK-STALE",
                    "The active runtime artifact changed. Refresh analytics before benchmarking.");
        }
        runtime.evaluate(deployment.getArtifactJson(), inputs);
        for (var index = 0; index < command.warmupIterations(); index++) {
            runtime.evaluate(deployment.getArtifactJson(), inputs);
        }
        var timings = new long[command.iterations()];
        CapturedDecisionRuntime.Evaluation evaluated = null;
        var totalStarted = System.nanoTime();
        for (var index = 0; index < timings.length; index++) {
            var started = System.nanoTime();
            evaluated = runtime.evaluate(deployment.getArtifactJson(), inputs);
            timings[index] = Math.max(0, (System.nanoTime() - started) / 1_000);
        }
        var totalNanos = Math.max(1, System.nanoTime() - totalStarted);
        Arrays.sort(timings);
        var mean = Math.round(Arrays.stream(timings).average().orElse(0));
        var run = new RuntimeBenchmarkRun(actor.tenantId(), projectId, serviceId, deployment.getId(),
                deployment.getDecisionVersionId(), command.clientRequestId(), requestFingerprint,
                deployment.getArtifactFingerprint(), fingerprint(inputs), fingerprint(evaluated.outputs()),
                command.iterations(), command.warmupIterations(), totalNanos / 1_000, timings[0], mean,
                percentile(timings, 0.50), percentile(timings, 0.95), percentile(timings, 0.99),
                timings[timings.length - 1], command.iterations() * 1_000_000_000d / totalNanos,
                actor.userId(), actor.displayName(), Instant.now(clock));
        benchmarks.saveAndFlush(run);
        audit.record(actor.tenantId(), actor.userId(), "RUNTIME_BENCHMARK_COMPLETED", "RUNTIME_BENCHMARK",
                run.getId(), Map.of("serviceId", serviceId, "deploymentId", deployment.getId(),
                        "iterations", run.getIterations(), "p95Micros", run.getP95Micros(),
                        "artifactFingerprint", run.getArtifactFingerprint()));
        return view(run);
    }

    private AnalyticsSnapshot summarize(AnalyticsWindow window, TraceChannel selectedChannel, Instant since,
            Instant now, long totalAvailable, List<DecisionTrace> values) {
        var latencies = values.stream().mapToLong(DecisionTrace::getDurationMicros).sorted().toArray();
        var succeeded = values.stream().filter(value -> value.getStatus() == TraceStatus.SUCCEEDED).count();
        var failed = values.size() - succeeded;
        var buckets = new ArrayList<MutableBucket>();
        var bucketCount = Math.toIntExact(window.duration.toSeconds() / window.bucket.toSeconds());
        for (var index = 0; index < bucketCount; index++) {
            var start = since.plus(window.bucket.multipliedBy(index));
            buckets.add(new MutableBucket(start, start.plus(window.bucket)));
        }
        for (var trace : values) {
            var index = (int) (Duration.between(since, trace.getCreatedAt()).toSeconds() / window.bucket.toSeconds());
            if (index >= 0 && index < buckets.size()) buckets.get(index).add(trace);
        }
        var channelStats = values.stream().collect(Collectors.groupingBy(DecisionTrace::getChannel,
                () -> new EnumMap<>(TraceChannel.class), Collectors.toList())).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> channel(entry.getKey(), entry.getValue())).toList();
        var errorStats = values.stream().filter(value -> value.getStatus() == TraceStatus.FAILED)
                .map(value -> value.getErrorCode() == null ? "UNCLASSIFIED" : value.getErrorCode())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey)).limit(5)
                .map(entry -> new ErrorStat(entry.getKey(), entry.getValue())).toList();
        return new AnalyticsSnapshot(window.name(), selectedChannel == null ? "ALL" : selectedChannel.name(),
                since, now, values.isEmpty() ? null : values.getFirst().getCreatedAt(), values.size(), totalAvailable,
                totalAvailable > TRACE_LIMIT, succeeded, failed, values.isEmpty() ? 0 : succeeded * 100d / values.size(),
                average(latencies), quantile(latencies, .50), quantile(latencies, .95), quantile(latencies, .99),
                latencies.length == 0 ? 0 : latencies[latencies.length - 1], window.bucketLabel,
                buckets.stream().map(MutableBucket::view).toList(), channelStats, errorStats);
    }

    private ChannelStat channel(TraceChannel channel, List<DecisionTrace> values) {
        var latencies = values.stream().mapToLong(DecisionTrace::getDurationMicros).sorted().toArray();
        var success = values.stream().filter(value -> value.getStatus() == TraceStatus.SUCCEEDED).count();
        return new ChannelStat(channel, values.size(), success, values.size() - success, quantile(latencies, .95));
    }

    private ActiveArtifact active(RuleCraftPrincipal actor, UUID projectId, UUID serviceId) {
        return environments.findByServiceIdAndTenantIdAndProjectId(serviceId, actor.tenantId(), projectId)
                .filter(value -> value.getActiveDeploymentId() != null)
                .flatMap(value -> deployments.findByIdAndServiceIdAndTenantIdAndProjectId(
                        value.getActiveDeploymentId(), serviceId, actor.tenantId(), projectId))
                .filter(value -> value.getState() == RuntimeDeploymentState.ACTIVE)
                .map(value -> new ActiveArtifact(value.getId(), value.getSequenceNumber(),
                        value.getDecisionVersionId(), value.getArtifactFingerprint())).orElse(null);
    }

    private com.rulecraft.decisionops.deployment.domain.RuntimeDeployment requireActive(
            RuleCraftPrincipal actor, UUID projectId, UUID serviceId) {
        var environment = environments.findByServiceIdAndTenantIdAndProjectId(serviceId, actor.tenantId(), projectId)
                .orElseThrow(() -> conflict("RC-BENCHMARK-NO-RUNTIME",
                        "Deploy a promoted artifact before running a benchmark."));
        if (environment.getActiveDeploymentId() == null) throw conflict("RC-BENCHMARK-NO-RUNTIME",
                "The local sandbox has no active artifact to benchmark.");
        var deployment = deployments.findByIdAndServiceIdAndTenantIdAndProjectId(environment.getActiveDeploymentId(),
                serviceId, actor.tenantId(), projectId).orElseThrow(() -> conflict("RC-BENCHMARK-ACTIVE-MISSING",
                        "The active runtime pointer is inconsistent. Repair it before benchmarking."));
        if (deployment.getState() != RuntimeDeploymentState.ACTIVE) throw conflict("RC-BENCHMARK-ACTIVE-STATE",
                "The active runtime pointer does not reference an active deployment.");
        return deployment;
    }

    private BenchmarkView exactRetry(RuntimeBenchmarkRun value, String requestFingerprint) {
        if (!MessageDigest.isEqual(value.getRequestFingerprint().getBytes(StandardCharsets.UTF_8),
                requestFingerprint.getBytes(StandardCharsets.UTF_8))) throw conflict("RC-BENCHMARK-REQUEST-CONFLICT",
                        "This request ID already benchmarked different inputs or iteration settings.");
        return view(value);
    }

    private BenchmarkView view(RuntimeBenchmarkRun value) {
        return new BenchmarkView(value.getId(), value.getDeploymentId(), value.getDecisionVersionId(),
                value.getArtifactFingerprint(), value.getInputFingerprint(), value.getOutputFingerprint(),
                value.getIterations(), value.getWarmupIterations(), value.getTotalDurationMicros(),
                value.getMinMicros(), value.getMeanMicros(), value.getP50Micros(), value.getP95Micros(),
                value.getP99Micros(), value.getMaxMicros(), value.getThroughputPerSecond(),
                value.getCapturedByName(), value.getCapturedAt());
    }

    private TraceChannel channel(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) return null;
        try { return TraceChannel.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException failure) { bad("RC-ANALYTICS-CHANNEL", "Choose a supported runtime channel."); return null; }
    }
    private long percentile(long[] values, double percentile) { return values[Math.max(0,
            Math.min(values.length - 1, (int) Math.ceil(percentile * values.length) - 1))]; }
    private long quantile(long[] values, double percentile) { return values.length == 0 ? 0 : percentile(values, percentile); }
    private long average(long[] values) { return values.length == 0 ? 0 : Math.round(Arrays.stream(values).average().orElse(0)); }
    private String fingerprint(Object value) { return sha256(json(sort(mapper.valueToTree(value)))); }
    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull()) return NullNode.getInstance();
        if (value.isObject()) { var result = mapper.createObjectNode(); value.propertyNames().stream().sorted()
                .forEach(key -> result.set(key, sort(value.get(key)))); return result; }
        if (value.isArray()) { var result = mapper.createArrayNode(); value.values().forEach(item -> result.add(sort(item))); return result; }
        return value;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); }
        catch (JacksonException failure) { throw new IllegalStateException("Runtime analytics evidence could not be serialized.", failure); } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private void bad(String code, String detail) { throw new DomainProblemException(HttpStatus.BAD_REQUEST, code, detail); }
    private DomainProblemException conflict(String code, String detail) { return new DomainProblemException(HttpStatus.CONFLICT, code, detail); }
    private DomainProblemException serviceNotFound() { return new DomainProblemException(HttpStatus.NOT_FOUND,
            "RC-ANALYTICS-SERVICE-NOT-FOUND", "Decision service not found."); }

    private enum AnalyticsWindow {
        HOURS_24(Duration.ofHours(24), Duration.ofHours(1), "HOURLY"),
        DAYS_7(Duration.ofDays(7), Duration.ofHours(6), "SIX_HOURLY"),
        DAYS_30(Duration.ofDays(30), Duration.ofDays(1), "DAILY");
        private final Duration duration; private final Duration bucket; private final String bucketLabel;
        AnalyticsWindow(Duration duration, Duration bucket, String bucketLabel) {
            this.duration = duration; this.bucket = bucket; this.bucketLabel = bucketLabel;
        }
        private static AnalyticsWindow parse(String raw) {
            if (raw == null || raw.isBlank()) return HOURS_24;
            try { return valueOf(raw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException failure) { throw new DomainProblemException(HttpStatus.BAD_REQUEST,
                    "RC-ANALYTICS-WINDOW", "Choose HOURS_24, DAYS_7, or DAYS_30."); }
        }
    }

    private static final class MutableBucket {
        private final Instant start; private final Instant end; private int count; private int failures;
        private final List<Long> latency = new ArrayList<>();
        private MutableBucket(Instant start, Instant end) { this.start = start; this.end = end; }
        private void add(DecisionTrace trace) { count++; if (trace.getStatus() == TraceStatus.FAILED) failures++;
            latency.add(trace.getDurationMicros()); }
        private TimeBucket view() { var values = latency.stream().mapToLong(Long::longValue).sorted().toArray();
            return new TimeBucket(start, end, count, failures, values.length == 0 ? 0
                    : values[Math.max(0, (int) Math.ceil(.95 * values.length) - 1)]); }
    }

    public record AnalyticsWorkspace(String serviceKey, String serviceName, AnalyticsSnapshot analytics,
            ActiveArtifact activeArtifact, List<BenchmarkView> benchmarks, boolean canBenchmark, String accessRole) {}
    public record AnalyticsSnapshot(String window, String channel, Instant from, Instant to, Instant latestEventAt,
            int analyzedCount, long totalAvailable, boolean truncated, long succeededCount, long failedCount,
            double successRate, long meanMicros, long p50Micros, long p95Micros, long p99Micros, long maxMicros,
            String bucketGrain, List<TimeBucket> buckets, List<ChannelStat> channels, List<ErrorStat> errors) {}
    public record TimeBucket(Instant start, Instant end, int count, int failedCount, long p95Micros) {}
    public record ChannelStat(TraceChannel channel, int count, long succeededCount, long failedCount, long p95Micros) {}
    public record ErrorStat(String code, long count) {}
    public record ActiveArtifact(UUID deploymentId, int sequenceNumber, UUID decisionVersionId, String artifactFingerprint) {}
    public record BenchmarkCommand(UUID clientRequestId, UUID expectedDeploymentId,
            Integer iterations, Integer warmupIterations,
            Map<String, JsonNode> inputs) {}
    public record BenchmarkView(UUID id, UUID deploymentId, UUID decisionVersionId, String artifactFingerprint,
            String inputFingerprint, String outputFingerprint, int iterations, int warmupIterations,
            long totalDurationMicros, long minMicros, long meanMicros, long p50Micros, long p95Micros,
            long p99Micros, long maxMicros, double throughputPerSecond, String capturedByName, Instant capturedAt) {}
}

package com.rulecraft.decisionops.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runtime_benchmark_run")
public class RuntimeBenchmarkRun {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "decision_service_id", nullable = false) private UUID serviceId;
    @Column(name = "deployment_id", nullable = false) private UUID deploymentId;
    @Column(name = "decision_version_id", nullable = false) private UUID decisionVersionId;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "artifact_fingerprint", nullable = false, length = 64) private String artifactFingerprint;
    @Column(name = "input_fingerprint", nullable = false, length = 64) private String inputFingerprint;
    @Column(name = "output_fingerprint", nullable = false, length = 64) private String outputFingerprint;
    @Column(nullable = false) private int iterations;
    @Column(name = "warmup_iterations", nullable = false) private int warmupIterations;
    @Column(name = "total_duration_micros", nullable = false) private long totalDurationMicros;
    @Column(name = "min_micros", nullable = false) private long minMicros;
    @Column(name = "mean_micros", nullable = false) private long meanMicros;
    @Column(name = "p50_micros", nullable = false) private long p50Micros;
    @Column(name = "p95_micros", nullable = false) private long p95Micros;
    @Column(name = "p99_micros", nullable = false) private long p99Micros;
    @Column(name = "max_micros", nullable = false) private long maxMicros;
    @Column(name = "throughput_per_second", nullable = false) private double throughputPerSecond;
    @Column(name = "captured_by") private UUID capturedBy;
    @Column(name = "captured_by_name", nullable = false, length = 160) private String capturedByName;
    @Column(name = "captured_at", nullable = false) private Instant capturedAt;

    protected RuntimeBenchmarkRun() {}

    public RuntimeBenchmarkRun(UUID tenantId, UUID projectId, UUID serviceId, UUID deploymentId,
            UUID decisionVersionId, UUID clientRequestId, String requestFingerprint,
            String artifactFingerprint, String inputFingerprint, String outputFingerprint,
            int iterations, int warmupIterations, long totalDurationMicros, long minMicros,
            long meanMicros, long p50Micros, long p95Micros, long p99Micros, long maxMicros,
            double throughputPerSecond, UUID capturedBy, String capturedByName, Instant capturedAt) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.projectId = projectId; this.serviceId = serviceId;
        this.deploymentId = deploymentId; this.decisionVersionId = decisionVersionId;
        this.clientRequestId = clientRequestId; this.requestFingerprint = requestFingerprint;
        this.artifactFingerprint = artifactFingerprint; this.inputFingerprint = inputFingerprint;
        this.outputFingerprint = outputFingerprint; this.iterations = iterations;
        this.warmupIterations = warmupIterations; this.totalDurationMicros = totalDurationMicros;
        this.minMicros = minMicros; this.meanMicros = meanMicros; this.p50Micros = p50Micros;
        this.p95Micros = p95Micros; this.p99Micros = p99Micros; this.maxMicros = maxMicros;
        this.throughputPerSecond = throughputPerSecond; this.capturedBy = capturedBy;
        this.capturedByName = capturedByName; this.capturedAt = capturedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getDeploymentId() { return deploymentId; }
    public UUID getDecisionVersionId() { return decisionVersionId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getArtifactFingerprint() { return artifactFingerprint; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getOutputFingerprint() { return outputFingerprint; }
    public int getIterations() { return iterations; }
    public int getWarmupIterations() { return warmupIterations; }
    public long getTotalDurationMicros() { return totalDurationMicros; }
    public long getMinMicros() { return minMicros; }
    public long getMeanMicros() { return meanMicros; }
    public long getP50Micros() { return p50Micros; }
    public long getP95Micros() { return p95Micros; }
    public long getP99Micros() { return p99Micros; }
    public long getMaxMicros() { return maxMicros; }
    public double getThroughputPerSecond() { return throughputPerSecond; }
    public String getCapturedByName() { return capturedByName; }
    public Instant getCapturedAt() { return capturedAt; }
}

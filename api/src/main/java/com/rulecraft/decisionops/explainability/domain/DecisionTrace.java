package com.rulecraft.decisionops.explainability.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "decision_trace")
public class DecisionTrace {
    @Id private UUID id;
    @Column(name = "evaluation_id", nullable = false, unique = true) private UUID evaluationId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "decision_service_id", nullable = false) private UUID serviceId;
    @Column(name = "deployment_id", nullable = false) private UUID deploymentId;
    @Column(name = "decision_version_id", nullable = false) private UUID decisionVersionId;
    @Column(name = "credential_id") private UUID credentialId;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "subject_label", nullable = false, length = 160) private String subjectLabel;
    @Column(name = "client_request_id") private UUID clientRequestId;
    @Column(name = "correlation_id", length = 80) private String correlationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TraceChannel channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TraceStatus status;
    @Column(name = "artifact_fingerprint", nullable = false, length = 64) private String artifactFingerprint;
    @Column(name = "input_fingerprint", nullable = false, length = 64) private String inputFingerprint;
    @Column(name = "output_fingerprint", length = 64) private String outputFingerprint;
    @Column(name = "input_keys_json", nullable = false, columnDefinition = "text") private String inputKeysJson;
    @Column(name = "output_keys_json", nullable = false, columnDefinition = "text") private String outputKeysJson;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "error_detail", length = 600) private String errorDetail;
    @Column(name = "step_count", nullable = false) private int stepCount;
    @Column(name = "duration_micros", nullable = false) private long durationMicros;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "trace_id", nullable = false)
    @OrderBy("position asc")
    private List<DecisionTraceStep> steps = new ArrayList<>();

    protected DecisionTrace() {}

    public DecisionTrace(UUID evaluationId, UUID tenantId, UUID projectId, UUID serviceId,
            UUID deploymentId, UUID decisionVersionId, UUID credentialId, UUID actorUserId,
            String subjectLabel, UUID clientRequestId, String correlationId, TraceChannel channel,
            TraceStatus status, String artifactFingerprint, String inputFingerprint,
            String outputFingerprint, String inputKeysJson, String outputKeysJson,
            String errorCode, String errorDetail, long durationMicros, Instant createdAt,
            List<DecisionTraceStep> steps) {
        this.id = UUID.randomUUID();
        this.evaluationId = evaluationId;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.deploymentId = deploymentId;
        this.decisionVersionId = decisionVersionId;
        this.credentialId = credentialId;
        this.actorUserId = actorUserId;
        this.subjectLabel = subjectLabel;
        this.clientRequestId = clientRequestId;
        this.correlationId = correlationId;
        this.channel = channel;
        this.status = status;
        this.artifactFingerprint = artifactFingerprint;
        this.inputFingerprint = inputFingerprint;
        this.outputFingerprint = outputFingerprint;
        this.inputKeysJson = inputKeysJson;
        this.outputKeysJson = outputKeysJson;
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        this.stepCount = steps.size();
        this.durationMicros = durationMicros;
        this.createdAt = createdAt;
        this.steps.addAll(steps);
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getDeploymentId() { return deploymentId; }
    public UUID getDecisionVersionId() { return decisionVersionId; }
    public UUID getCredentialId() { return credentialId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getSubjectLabel() { return subjectLabel; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getCorrelationId() { return correlationId; }
    public TraceChannel getChannel() { return channel; }
    public TraceStatus getStatus() { return status; }
    public String getArtifactFingerprint() { return artifactFingerprint; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getOutputFingerprint() { return outputFingerprint; }
    public String getInputKeysJson() { return inputKeysJson; }
    public String getOutputKeysJson() { return outputKeysJson; }
    public String getErrorCode() { return errorCode; }
    public String getErrorDetail() { return errorDetail; }
    public int getStepCount() { return stepCount; }
    public long getDurationMicros() { return durationMicros; }
    public Instant getCreatedAt() { return createdAt; }
    public List<DecisionTraceStep> getSteps() { return List.copyOf(steps); }
}

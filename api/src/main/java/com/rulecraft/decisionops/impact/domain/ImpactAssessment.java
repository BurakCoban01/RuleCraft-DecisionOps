package com.rulecraft.decisionops.impact.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "impact_assessment")
public class ImpactAssessment {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "decision_service_id", nullable = false) private UUID serviceId;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(name = "source_fingerprint", nullable = false, length = 64) private String sourceFingerprint;
    @Column(name = "snapshot_fingerprint", nullable = false, length = 64) private String snapshotFingerprint;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 16) private ImpactRisk riskLevel;
    @Column(name = "dependency_count", nullable = false) private int dependencyCount;
    @Column(name = "finding_count", nullable = false) private int findingCount;
    @Column(name = "blocker_count", nullable = false) private int blockerCount;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "text") private String evidenceJson;
    @Column(name = "captured_by") private UUID capturedBy;
    @Column(name = "captured_by_name", nullable = false, length = 160) private String capturedByName;
    @Column(name = "captured_at", nullable = false) private Instant capturedAt;

    protected ImpactAssessment() {}

    public ImpactAssessment(UUID tenantId, UUID projectId, UUID serviceId, UUID clientRequestId,
            String sourceFingerprint, String snapshotFingerprint, ImpactRisk riskLevel,
            int dependencyCount, int findingCount, int blockerCount, String evidenceJson,
            UUID capturedBy, String capturedByName, Instant capturedAt) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.projectId = projectId; this.serviceId = serviceId;
        this.clientRequestId = clientRequestId; this.sourceFingerprint = sourceFingerprint;
        this.snapshotFingerprint = snapshotFingerprint; this.riskLevel = riskLevel;
        this.dependencyCount = dependencyCount; this.findingCount = findingCount; this.blockerCount = blockerCount;
        this.evidenceJson = evidenceJson; this.capturedBy = capturedBy; this.capturedByName = capturedByName;
        this.capturedAt = capturedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public ImpactRisk getRiskLevel() { return riskLevel; }
    public int getDependencyCount() { return dependencyCount; }
    public int getFindingCount() { return findingCount; }
    public int getBlockerCount() { return blockerCount; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getCapturedByName() { return capturedByName; }
    public Instant getCapturedAt() { return capturedAt; }
}

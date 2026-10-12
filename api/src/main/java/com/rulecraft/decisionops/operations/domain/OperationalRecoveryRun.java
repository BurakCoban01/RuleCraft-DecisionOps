package com.rulecraft.decisionops.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operational_recovery_run")
public class OperationalRecoveryRun {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(name = "source_fingerprint", nullable = false, length = 64) private String sourceFingerprint;
    @Column(name = "result_fingerprint", nullable = false, length = 64) private String resultFingerprint;
    @Column(name = "interrupted_matrices", nullable = false) private int interruptedMatrices;
    @Column(name = "cleared_runtime_pointers", nullable = false) private int clearedRuntimePointers;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "executed_by") private UUID executedBy;
    @Column(name = "executed_by_name", nullable = false, length = 160) private String executedByName;
    @Column(name = "executed_at", nullable = false) private Instant executedAt;

    protected OperationalRecoveryRun() {}
    public OperationalRecoveryRun(UUID tenantId, UUID clientRequestId, String sourceFingerprint,
            String resultFingerprint, int interruptedMatrices, int clearedRuntimePointers, String status,
            UUID executedBy, String executedByName, Instant executedAt) {
        id = UUID.randomUUID(); this.tenantId = tenantId; this.clientRequestId = clientRequestId;
        this.sourceFingerprint = sourceFingerprint; this.resultFingerprint = resultFingerprint;
        this.interruptedMatrices = interruptedMatrices; this.clearedRuntimePointers = clearedRuntimePointers;
        this.status = status; this.executedBy = executedBy; this.executedByName = executedByName;
        this.executedAt = executedAt;
    }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public String getResultFingerprint() { return resultFingerprint; }
    public int getInterruptedMatrices() { return interruptedMatrices; }
    public int getClearedRuntimePointers() { return clearedRuntimePointers; }
    public String getStatus() { return status; }
    public String getExecutedByName() { return executedByName; }
    public Instant getExecutedAt() { return executedAt; }
}

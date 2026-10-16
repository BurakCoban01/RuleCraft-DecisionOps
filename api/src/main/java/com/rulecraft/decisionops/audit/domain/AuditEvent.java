package com.rulecraft.decisionops.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "actor_user_id", updatable = false) private UUID actorUserId;
    @Column(nullable = false, length = 80, updatable = false) private String action;
    @Column(name = "target_type", nullable = false, length = 64, updatable = false) private String targetType;
    @Column(name = "target_id", nullable = false, length = 80, updatable = false) private String targetId;
    @Column(name = "details_json", nullable = false, columnDefinition = "text", updatable = false) private String detailsJson;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(UUID tenantId, UUID actorUserId, String action, String targetType,
            String targetId, String detailsJson, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detailsJson = detailsJson;
        this.occurredAt = occurredAt;
    }
}

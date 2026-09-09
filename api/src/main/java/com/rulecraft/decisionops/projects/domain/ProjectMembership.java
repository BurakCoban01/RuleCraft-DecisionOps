package com.rulecraft.decisionops.projects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_membership")
public class ProjectMembership {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProjectRole role;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected ProjectMembership() {
    }

    public ProjectMembership(UUID tenantId, UUID projectId, UUID userId, ProjectRole role, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getUserId() { return userId; }
    public ProjectRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void changeRole(ProjectRole role) {
        this.role = role;
    }
}

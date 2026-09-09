package com.rulecraft.decisionops.projects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_key", nullable = false, length = 24)
    private String projectKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 600)
    private String description;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Project() {
    }

    public Project(UUID tenantId, String projectKey, String name, String description, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.projectKey = projectKey.strip().toUpperCase(Locale.ROOT);
        this.name = name.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProjectKey() { return projectKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

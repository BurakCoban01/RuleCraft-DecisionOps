package com.rulecraft.decisionops.graph.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="decision_graph")
public class DecisionGraph {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="project_id",nullable=false,updatable=false) private UUID projectId;
    @Column(name="decision_service_id",nullable=false,updatable=false) private UUID decisionServiceId;
    @Enumerated(EnumType.STRING) @Column(name="validation_status",nullable=false,length=16) private GraphValidationStatus validationStatus;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected DecisionGraph() {}
    public DecisionGraph(UUID tenantId,UUID projectId,UUID decisionServiceId,UUID createdBy,Instant now){
        this.id=UUID.randomUUID();this.tenantId=tenantId;this.projectId=projectId;this.decisionServiceId=decisionServiceId;
        this.createdBy=createdBy;this.createdAt=now;this.updatedAt=now;this.validationStatus=GraphValidationStatus.INVALID;
    }
    public void touch(GraphValidationStatus status,Instant now){this.validationStatus=status;this.updatedAt=now;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getProjectId(){return projectId;}
    public UUID getDecisionServiceId(){return decisionServiceId;} public GraphValidationStatus getValidationStatus(){return validationStatus;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}

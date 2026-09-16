package com.rulecraft.decisionops.tables.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="decision_table_definition")
public class DecisionTableDefinition {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="project_id",nullable=false,updatable=false) private UUID projectId;
    @Column(name="graph_id",nullable=false,updatable=false) private UUID graphId;
    @Column(name="node_id",nullable=false,updatable=false) private UUID nodeId;
    @Enumerated(EnumType.STRING) @Column(name="hit_policy",nullable=false,length=16) private HitPolicy hitPolicy;
    @Enumerated(EnumType.STRING) @Column(name="validation_status",nullable=false,length=16) private TableValidationStatus validationStatus;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected DecisionTableDefinition() {}
    public DecisionTableDefinition(UUID tenantId,UUID projectId,UUID graphId,UUID nodeId,UUID createdBy,Instant now){this.id=UUID.randomUUID();
        this.tenantId=tenantId;this.projectId=projectId;this.graphId=graphId;this.nodeId=nodeId;this.createdBy=createdBy;this.createdAt=now;
        this.hitPolicy=HitPolicy.UNIQUE;this.validationStatus=TableValidationStatus.INVALID;this.updatedAt=now;}
    public void update(HitPolicy hitPolicy,TableValidationStatus status,Instant now){this.hitPolicy=hitPolicy;this.validationStatus=status;this.updatedAt=now;}
    public UUID getId(){return id;} public UUID getGraphId(){return graphId;} public UUID getNodeId(){return nodeId;}
    public HitPolicy getHitPolicy(){return hitPolicy;} public TableValidationStatus getValidationStatus(){return validationStatus;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}

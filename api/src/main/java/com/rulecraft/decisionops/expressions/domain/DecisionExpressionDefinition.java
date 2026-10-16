package com.rulecraft.decisionops.expressions.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="decision_expression_definition")
public class DecisionExpressionDefinition {
    @Id private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="project_id",nullable=false)private UUID projectId;
    @Column(name="graph_id",nullable=false)private UUID graphId;@Column(name="node_id",nullable=false)private UUID nodeId;
    @Column(name="language_version",nullable=false,length=16)private String languageVersion;@Column(name="source_text",nullable=false,length=12000)private String sourceText;
    @Enumerated(EnumType.STRING)@Column(name="validation_status",nullable=false,length=16)private ExpressionValidationStatus validationStatus;
    @Column(name="created_by",nullable=false)private UUID createdBy;@Column(name="created_at",nullable=false)private Instant createdAt;@Column(name="updated_at",nullable=false)private Instant updatedAt;@Version private long version;
    protected DecisionExpressionDefinition(){}
    public DecisionExpressionDefinition(UUID tenantId,UUID projectId,UUID graphId,UUID nodeId,UUID createdBy,Instant now){this.id=UUID.randomUUID();this.tenantId=tenantId;this.projectId=projectId;this.graphId=graphId;this.nodeId=nodeId;this.createdBy=createdBy;this.createdAt=now;this.updatedAt=now;this.languageVersion="RCEL-1";this.sourceText="";this.validationStatus=ExpressionValidationStatus.INVALID;}
    public void update(String source,ExpressionValidationStatus status,Instant now){this.sourceText=source;this.validationStatus=status;this.updatedAt=now;}
    public UUID getId(){return id;}public UUID getNodeId(){return nodeId;}public String getLanguageVersion(){return languageVersion;}public String getSourceText(){return sourceText;}public ExpressionValidationStatus getValidationStatus(){return validationStatus;}public Instant getUpdatedAt(){return updatedAt;}public long getVersion(){return version;}
}

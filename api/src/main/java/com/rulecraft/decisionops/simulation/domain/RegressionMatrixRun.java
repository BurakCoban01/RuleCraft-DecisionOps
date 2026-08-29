package com.rulecraft.decisionops.simulation.domain;

import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;

@Entity @Table(name="regression_matrix_run")
public class RegressionMatrixRun {
    @Id private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="project_id",nullable=false)private UUID projectId;@Column(name="decision_service_id",nullable=false)private UUID decisionServiceId;@Column(name="client_request_id",nullable=false)private UUID clientRequestId;
    @Column(name="selection_json",nullable=false,columnDefinition="text")private String selectionJson;@Enumerated(EnumType.STRING)@Column(nullable=false,length=16)private MatrixRunStatus status;@Column(name="selected_count",nullable=false)private int selectedCount;@Column(name="passed_count",nullable=false)private int passedCount;@Column(name="failed_count",nullable=false)private int failedCount;@Column(name="error_count",nullable=false)private int errorCount;
    @Column(name="initiated_by",nullable=false)private UUID initiatedBy;@Column(name="started_at",nullable=false)private Instant startedAt;@Column(name="completed_at")private Instant completedAt;
    protected RegressionMatrixRun(){}
    public RegressionMatrixRun(UUID tenantId,UUID projectId,UUID serviceId,UUID requestId,String selection,int count,UUID actor,Instant now){this.id=UUID.randomUUID();this.tenantId=tenantId;this.projectId=projectId;this.decisionServiceId=serviceId;this.clientRequestId=requestId;this.selectionJson=selection;this.status=MatrixRunStatus.RUNNING;this.selectedCount=count;this.initiatedBy=actor;this.startedAt=now;}
    public void complete(int passed,int failed,int errors,Instant now){this.status=MatrixRunStatus.COMPLETED;this.passedCount=passed;this.failedCount=failed;this.errorCount=errors;this.completedAt=now;}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getProjectId(){return projectId;}public UUID getDecisionServiceId(){return decisionServiceId;}public UUID getClientRequestId(){return clientRequestId;}public String getSelectionJson(){return selectionJson;}public MatrixRunStatus getStatus(){return status;}public int getSelectedCount(){return selectedCount;}public int getPassedCount(){return passedCount;}public int getFailedCount(){return failedCount;}public int getErrorCount(){return errorCount;}public Instant getStartedAt(){return startedAt;}public Instant getCompletedAt(){return completedAt;}
}

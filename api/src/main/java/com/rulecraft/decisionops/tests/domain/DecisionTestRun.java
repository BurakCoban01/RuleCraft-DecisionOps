package com.rulecraft.decisionops.tests.domain;

import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;

@Entity @Table(name="decision_test_run")
public class DecisionTestRun {
    @Id private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="project_id",nullable=false)private UUID projectId;@Column(name="decision_service_id",nullable=false)private UUID decisionServiceId;@Column(name="test_case_id",nullable=false)private UUID testCaseId;
    @Column(name="test_case_version",nullable=false)private long testCaseVersion;@Column(name="evaluation_id",nullable=false)private UUID evaluationId;@Column(name="graph_version",nullable=false)private long graphVersion;@Enumerated(EnumType.STRING)@Column(nullable=false,length=16)private TestRunStatus status;
    @Column(name="expected_outputs_json",nullable=false,columnDefinition="text")private String expectedOutputsJson;@Column(name="actual_outputs_json",nullable=false,columnDefinition="text")private String actualOutputsJson;@Column(name="decision_results_json",nullable=false,columnDefinition="text")private String decisionResultsJson;
    @Column(name="executed_by",nullable=false)private UUID executedBy;@Column(name="executed_at",nullable=false)private Instant executedAt;
    protected DecisionTestRun(){}
    public DecisionTestRun(DecisionTestCase test,UUID evaluationId,long graphVersion,TestRunStatus status,String expected,String actual,String decisions,UUID actor,Instant now){this.id=UUID.randomUUID();this.tenantId=test.getTenantId();this.projectId=test.getProjectId();this.decisionServiceId=test.getDecisionServiceId();this.testCaseId=test.getId();this.testCaseVersion=test.getVersion();this.evaluationId=evaluationId;this.graphVersion=graphVersion;this.status=status;this.expectedOutputsJson=expected;this.actualOutputsJson=actual;this.decisionResultsJson=decisions;this.executedBy=actor;this.executedAt=now;}
    public UUID getId(){return id;}public UUID getTestCaseId(){return testCaseId;}public long getTestCaseVersion(){return testCaseVersion;}public UUID getEvaluationId(){return evaluationId;}public long getGraphVersion(){return graphVersion;}public TestRunStatus getStatus(){return status;}public String getExpectedOutputsJson(){return expectedOutputsJson;}public String getActualOutputsJson(){return actualOutputsJson;}public String getDecisionResultsJson(){return decisionResultsJson;}public Instant getExecutedAt(){return executedAt;}
}

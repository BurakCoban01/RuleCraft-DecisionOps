package com.rulecraft.decisionops.tests.domain;

import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;

@Entity @Table(name="decision_test_case")
public class DecisionTestCase {
    @Id private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="project_id",nullable=false)private UUID projectId;@Column(name="decision_service_id",nullable=false)private UUID decisionServiceId;
    @Column(name="test_key",nullable=false,length=60)private String testKey;@Column(nullable=false,length=160)private String name;@Column(length=500)private String description;
    @Column(name="inputs_json",nullable=false,columnDefinition="text")private String inputsJson;@Column(name="expected_outputs_json",nullable=false,columnDefinition="text")private String expectedOutputsJson;
    @Enumerated(EnumType.STRING)@Column(name="validation_status",nullable=false,length=16)private TestCaseValidationStatus validationStatus;
    @Column(name="created_by",nullable=false)private UUID createdBy;@Column(name="updated_by",nullable=false)private UUID updatedBy;@Column(name="created_at",nullable=false)private Instant createdAt;@Column(name="updated_at",nullable=false)private Instant updatedAt;@Version private long version;
    protected DecisionTestCase(){}
    public DecisionTestCase(UUID tenantId,UUID projectId,UUID serviceId,String key,String name,String description,String inputs,String expected,UUID actor,Instant now){this.id=UUID.randomUUID();this.tenantId=tenantId;this.projectId=projectId;this.decisionServiceId=serviceId;update(key,name,description,inputs,expected,TestCaseValidationStatus.VALID,actor,now);this.createdBy=actor;this.createdAt=now;}
    public void update(String key,String name,String description,String inputs,String expected,TestCaseValidationStatus status,UUID actor,Instant now){this.testKey=key;this.name=name;this.description=description;this.inputsJson=inputs;this.expectedOutputsJson=expected;this.validationStatus=status;this.updatedBy=actor;this.updatedAt=now;}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getProjectId(){return projectId;}public UUID getDecisionServiceId(){return decisionServiceId;}public String getTestKey(){return testKey;}public String getName(){return name;}public String getDescription(){return description;}public String getInputsJson(){return inputsJson;}public String getExpectedOutputsJson(){return expectedOutputsJson;}public TestCaseValidationStatus getValidationStatus(){return validationStatus;}public Instant getUpdatedAt(){return updatedAt;}public long getVersion(){return version;}
}

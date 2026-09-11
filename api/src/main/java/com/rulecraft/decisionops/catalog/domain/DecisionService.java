package com.rulecraft.decisionops.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity @Table(name="decision_service")
public class DecisionService {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="project_id",nullable=false,updatable=false) private UUID projectId;
    @Column(name="service_key",nullable=false,length=40) private String serviceKey;
    @Column(nullable=false,length=160) private String name;
    @Column(length=600) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private DecisionServiceStatus status;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected DecisionService() {}
    public DecisionService(UUID tenantId,UUID projectId,String key,String name,String description,UUID createdBy,Instant now){
        this.id=UUID.randomUUID();this.tenantId=tenantId;this.projectId=projectId;this.createdBy=createdBy;this.createdAt=now;
        this.status=DecisionServiceStatus.ACTIVE;update(key,name,description,now);}
    public void update(String key,String name,String description,Instant now){this.serviceKey=key.strip().toUpperCase(Locale.ROOT);
        this.name=name.strip();this.description=description==null||description.isBlank()?null:description.strip();this.updatedAt=now;}
    public void changeStatus(DecisionServiceStatus status,Instant now){this.status=status;this.updatedAt=now;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getProjectId(){return projectId;}
    public String getServiceKey(){return serviceKey;} public String getName(){return name;} public String getDescription(){return description;}
    public DecisionServiceStatus getStatus(){return status;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}

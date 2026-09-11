package com.rulecraft.decisionops.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity @Table(name = "type_definition")
public class TypeDefinition {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false, updatable=false) private UUID tenantId;
    @Column(name="project_id", nullable=false, updatable=false) private UUID projectId;
    @Column(name="type_key", nullable=false, length=40) private String typeKey;
    @Column(nullable=false, length=160) private String name;
    @Column(length=600) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private TypeKind kind;
    @Enumerated(EnumType.STRING) @Column(name="item_builtin_type", length=24) private BuiltinType itemBuiltinType;
    @Column(name="item_custom_type_id") private UUID itemCustomTypeId;
    @Column(name="created_by", nullable=false, updatable=false) private UUID createdBy;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected TypeDefinition() {}
    public TypeDefinition(UUID tenantId, UUID projectId, String typeKey, String name, String description,
            TypeKind kind, UUID createdBy, Instant now) {
        this.id=UUID.randomUUID(); this.tenantId=tenantId; this.projectId=projectId; this.createdBy=createdBy;
        this.createdAt=now; update(typeKey,name,description,kind,null,null,now);
    }
    public void update(String typeKey, String name, String description, TypeKind kind,
            BuiltinType itemBuiltinType, UUID itemCustomTypeId, Instant now) {
        this.typeKey=typeKey.strip().toUpperCase(Locale.ROOT); this.name=name.strip();
        this.description=description==null||description.isBlank()?null:description.strip(); this.kind=kind;
        this.itemBuiltinType=itemBuiltinType; this.itemCustomTypeId=itemCustomTypeId; this.updatedAt=now;
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getProjectId(){return projectId;}
    public String getTypeKey(){return typeKey;} public String getName(){return name;} public String getDescription(){return description;}
    public TypeKind getKind(){return kind;} public BuiltinType getItemBuiltinType(){return itemBuiltinType;}
    public UUID getItemCustomTypeId(){return itemCustomTypeId;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}

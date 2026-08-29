package com.rulecraft.decisionops.catalog.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="type_enum_value")
public class TypeEnumValue {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="type_definition_id",nullable=false,updatable=false) private UUID typeDefinitionId;
    @Column(name="value_key",nullable=false,length=80) private String valueKey;
    @Column(nullable=false,length=160) private String label;
    @Column(nullable=false) private int position;
    protected TypeEnumValue() {}
    public TypeEnumValue(UUID tenantId,UUID typeDefinitionId,String valueKey,String label,int position){this.id=UUID.randomUUID();
        this.tenantId=tenantId;this.typeDefinitionId=typeDefinitionId;this.valueKey=valueKey.strip();this.label=label.strip();this.position=position;}
    public UUID getId(){return id;} public String getValueKey(){return valueKey;} public String getLabel(){return label;} public int getPosition(){return position;}
}

package com.rulecraft.decisionops.catalog.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="decision_service_field")
public class DecisionServiceField {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="decision_service_id",nullable=false,updatable=false) private UUID decisionServiceId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private FieldDirection direction;
    @Column(name="field_key",nullable=false,length=60) private String fieldKey;
    @Column(nullable=false,length=160) private String label;
    @Enumerated(EnumType.STRING) @Column(name="builtin_type",length=24) private BuiltinType builtinType;
    @Column(name="custom_type_id") private UUID customTypeId;
    @Column(nullable=false) private boolean required;
    @Column(nullable=false) private int position;
    protected DecisionServiceField() {}
    public DecisionServiceField(UUID tenantId,UUID decisionServiceId,FieldDirection direction,String fieldKey,String label,
            BuiltinType builtinType,UUID customTypeId,boolean required,int position){this.id=UUID.randomUUID();this.tenantId=tenantId;
        this.decisionServiceId=decisionServiceId;this.direction=direction;this.fieldKey=fieldKey.strip();this.label=label.strip();
        this.builtinType=builtinType;this.customTypeId=customTypeId;this.required=required;this.position=position;}
    public UUID getId(){return id;} public FieldDirection getDirection(){return direction;} public String getFieldKey(){return fieldKey;}
    public String getLabel(){return label;} public BuiltinType getBuiltinType(){return builtinType;} public UUID getCustomTypeId(){return customTypeId;}
    public boolean isRequired(){return required;} public int getPosition(){return position;}
}

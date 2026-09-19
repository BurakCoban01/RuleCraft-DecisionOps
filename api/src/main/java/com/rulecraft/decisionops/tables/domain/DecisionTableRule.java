package com.rulecraft.decisionops.tables.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="decision_table_rule")
public class DecisionTableRule {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="decision_table_id",nullable=false,updatable=false) private UUID decisionTableId;
    @Column(nullable=false) private int position;
    @Column(nullable=false) private boolean enabled;
    @Column(length=300) private String description;
    @Column(name="output_value_json",length=4000) private String outputValueJson;
    protected DecisionTableRule() {}
    public DecisionTableRule(UUID id,UUID tenantId,UUID tableId,int position,boolean enabled,String description,String output){this.id=id;
        this.tenantId=tenantId;this.decisionTableId=tableId;this.position=position;this.enabled=enabled;
        this.description=description==null||description.isBlank()?null:description.strip();this.outputValueJson=output==null?null:output.strip();}
    public UUID getId(){return id;} public int getPosition(){return position;} public boolean isEnabled(){return enabled;}
    public String getDescription(){return description;} public String getOutputValueJson(){return outputValueJson;}
}

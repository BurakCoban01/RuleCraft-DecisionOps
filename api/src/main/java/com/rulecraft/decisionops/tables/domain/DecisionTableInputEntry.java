package com.rulecraft.decisionops.tables.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="decision_table_input_entry")
public class DecisionTableInputEntry {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="decision_table_id",nullable=false,updatable=false) private UUID decisionTableId;
    @Column(name="rule_id",nullable=false,updatable=false) private UUID ruleId;
    @Column(name="input_port_id",nullable=false,updatable=false) private UUID inputPortId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private InputOperator operator;
    @Column(name="value_json",length=4000) private String valueJson;
    protected DecisionTableInputEntry() {}
    public DecisionTableInputEntry(UUID id,UUID tenantId,UUID tableId,UUID ruleId,UUID portId,InputOperator operator,String value){this.id=id;
        this.tenantId=tenantId;this.decisionTableId=tableId;this.ruleId=ruleId;this.inputPortId=portId;this.operator=operator;
        this.valueJson=value==null?null:value.strip();}
    public UUID getId(){return id;} public UUID getRuleId(){return ruleId;} public UUID getInputPortId(){return inputPortId;}
    public InputOperator getOperator(){return operator;} public String getValueJson(){return valueJson;}
}

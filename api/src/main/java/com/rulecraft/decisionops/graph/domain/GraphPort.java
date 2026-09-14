package com.rulecraft.decisionops.graph.domain;

import com.rulecraft.decisionops.catalog.domain.BuiltinType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="graph_port")
public class GraphPort {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="graph_id",nullable=false,updatable=false) private UUID graphId;
    @Column(name="node_id",nullable=false,updatable=false) private UUID nodeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private GraphPortDirection direction;
    @Column(name="port_key",nullable=false,length=60) private String portKey;
    @Column(nullable=false,length=160) private String label;
    @Enumerated(EnumType.STRING) @Column(name="builtin_type",length=24) private BuiltinType builtinType;
    @Column(name="custom_type_id") private UUID customTypeId;
    @Column(nullable=false) private boolean required;
    @Column(nullable=false) private int position;
    protected GraphPort() {}
    public GraphPort(UUID id,UUID tenantId,UUID graphId,UUID nodeId,GraphPortDirection direction,String portKey,String label,
            BuiltinType builtinType,UUID customTypeId,boolean required,int position){this.id=id;this.tenantId=tenantId;this.graphId=graphId;
        this.nodeId=nodeId;this.direction=direction;this.portKey=portKey;this.label=label;this.builtinType=builtinType;
        this.customTypeId=customTypeId;this.required=required;this.position=position;}
    public UUID getId(){return id;} public UUID getNodeId(){return nodeId;} public GraphPortDirection getDirection(){return direction;}
    public String getPortKey(){return portKey;} public String getLabel(){return label;} public BuiltinType getBuiltinType(){return builtinType;}
    public UUID getCustomTypeId(){return customTypeId;} public boolean isRequired(){return required;} public int getPosition(){return position;}
}

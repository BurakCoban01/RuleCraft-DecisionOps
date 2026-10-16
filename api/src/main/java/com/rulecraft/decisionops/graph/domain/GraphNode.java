package com.rulecraft.decisionops.graph.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="graph_node")
public class GraphNode {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="graph_id",nullable=false,updatable=false) private UUID graphId;
    @Column(name="node_key",nullable=false,length=60) private String nodeKey;
    @Column(nullable=false,length=160) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private GraphNodeKind kind;
    @Column(name="service_field_key",length=60) private String serviceFieldKey;
    @Column(name="position_x",nullable=false) private int positionX;
    @Column(name="position_y",nullable=false) private int positionY;
    protected GraphNode() {}
    public GraphNode(UUID id,UUID tenantId,UUID graphId,String nodeKey,String name,GraphNodeKind kind,String serviceFieldKey,int x,int y){
        this.id=id;this.tenantId=tenantId;this.graphId=graphId;this.nodeKey=nodeKey;this.name=name;this.kind=kind;
        this.serviceFieldKey=serviceFieldKey;this.positionX=x;this.positionY=y;
    }
    public void update(String nodeKey,String name,GraphNodeKind kind,String serviceFieldKey,int x,int y){this.nodeKey=nodeKey;this.name=name;
        this.kind=kind;this.serviceFieldKey=serviceFieldKey;this.positionX=x;this.positionY=y;}
    public UUID getId(){return id;} public UUID getGraphId(){return graphId;} public String getNodeKey(){return nodeKey;} public String getName(){return name;}
    public GraphNodeKind getKind(){return kind;} public String getServiceFieldKey(){return serviceFieldKey;}
    public int getPositionX(){return positionX;} public int getPositionY(){return positionY;}
}

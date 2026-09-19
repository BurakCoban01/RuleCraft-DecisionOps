package com.rulecraft.decisionops.graph.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="graph_edge")
public class GraphEdge {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="graph_id",nullable=false,updatable=false) private UUID graphId;
    @Column(name="source_port_id",nullable=false,updatable=false) private UUID sourcePortId;
    @Column(name="target_port_id",nullable=false,updatable=false) private UUID targetPortId;
    protected GraphEdge() {}
    public GraphEdge(UUID id,UUID tenantId,UUID graphId,UUID sourcePortId,UUID targetPortId){this.id=id;this.tenantId=tenantId;
        this.graphId=graphId;this.sourcePortId=sourcePortId;this.targetPortId=targetPortId;}
    public UUID getId(){return id;} public UUID getSourcePortId(){return sourcePortId;} public UUID getTargetPortId(){return targetPortId;}
}

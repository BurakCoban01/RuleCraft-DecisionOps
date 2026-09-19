package com.rulecraft.decisionops.graph.persistence;

import com.rulecraft.decisionops.graph.domain.GraphNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphNodeRepository extends JpaRepository<GraphNode,UUID> {
    List<GraphNode> findAllByGraphIdOrderByPositionXAscPositionYAsc(UUID graphId);
    void deleteAllByGraphId(UUID graphId);
}

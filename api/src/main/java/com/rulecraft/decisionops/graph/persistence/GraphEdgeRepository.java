package com.rulecraft.decisionops.graph.persistence;

import com.rulecraft.decisionops.graph.domain.GraphEdge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphEdgeRepository extends JpaRepository<GraphEdge,UUID> {
    List<GraphEdge> findAllByGraphIdOrderByIdAsc(UUID graphId);
    void deleteAllByGraphId(UUID graphId);
}

package com.rulecraft.decisionops.graph.persistence;

import com.rulecraft.decisionops.graph.domain.GraphPort;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphPortRepository extends JpaRepository<GraphPort,UUID> {
    List<GraphPort> findAllByGraphIdOrderByNodeIdAscDirectionAscPositionAsc(UUID graphId);
    void deleteAllByGraphId(UUID graphId);
    long countByCustomTypeId(UUID customTypeId);
}

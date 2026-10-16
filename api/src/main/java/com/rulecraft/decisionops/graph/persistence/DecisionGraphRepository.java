package com.rulecraft.decisionops.graph.persistence;

import com.rulecraft.decisionops.graph.domain.DecisionGraph;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionGraphRepository extends JpaRepository<DecisionGraph,UUID> {
    Optional<DecisionGraph> findByDecisionServiceIdAndTenantIdAndProjectId(UUID serviceId,UUID tenantId,UUID projectId);
}

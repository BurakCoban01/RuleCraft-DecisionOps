package com.rulecraft.decisionops.tables.persistence;

import com.rulecraft.decisionops.tables.domain.DecisionTableDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionTableRepository extends JpaRepository<DecisionTableDefinition,UUID> {
    Optional<DecisionTableDefinition> findByNodeIdAndTenantIdAndProjectId(UUID nodeId,UUID tenantId,UUID projectId);
    List<DecisionTableDefinition> findAllByGraphIdAndTenantIdOrderByUpdatedAtDesc(UUID graphId,UUID tenantId);
    boolean existsByNodeId(UUID nodeId);
}

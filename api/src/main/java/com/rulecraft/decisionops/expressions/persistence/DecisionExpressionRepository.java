package com.rulecraft.decisionops.expressions.persistence;
import com.rulecraft.decisionops.expressions.domain.DecisionExpressionDefinition;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DecisionExpressionRepository extends JpaRepository<DecisionExpressionDefinition,UUID>{Optional<DecisionExpressionDefinition> findByNodeIdAndTenantIdAndProjectId(UUID nodeId,UUID tenantId,UUID projectId);List<DecisionExpressionDefinition> findAllByGraphIdAndTenantIdOrderByUpdatedAtDesc(UUID graphId,UUID tenantId);boolean existsByNodeId(UUID nodeId);}

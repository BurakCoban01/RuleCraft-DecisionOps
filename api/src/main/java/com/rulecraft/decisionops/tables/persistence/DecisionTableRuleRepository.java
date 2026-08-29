package com.rulecraft.decisionops.tables.persistence;

import com.rulecraft.decisionops.tables.domain.DecisionTableRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionTableRuleRepository extends JpaRepository<DecisionTableRule,UUID> {
    List<DecisionTableRule> findAllByDecisionTableIdOrderByPositionAsc(UUID tableId);
    void deleteAllByDecisionTableId(UUID tableId);
}

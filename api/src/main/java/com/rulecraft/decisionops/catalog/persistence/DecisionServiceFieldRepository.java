package com.rulecraft.decisionops.catalog.persistence;

import com.rulecraft.decisionops.catalog.domain.DecisionServiceField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionServiceFieldRepository extends JpaRepository<DecisionServiceField,UUID> {
    List<DecisionServiceField> findAllByDecisionServiceIdOrderByDirectionAscPositionAsc(UUID decisionServiceId);
    long countByCustomTypeId(UUID customTypeId);
    void deleteAllByDecisionServiceId(UUID decisionServiceId);
}

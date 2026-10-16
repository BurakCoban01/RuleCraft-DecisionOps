package com.rulecraft.decisionops.workflow.persistence;
import com.rulecraft.decisionops.workflow.domain.DecisionPromotion;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface DecisionPromotionRepository extends JpaRepository<DecisionPromotion,UUID>{Optional<DecisionPromotion> findByServiceIdAndTenantIdAndProjectId(UUID serviceId,UUID tenantId,UUID projectId);}

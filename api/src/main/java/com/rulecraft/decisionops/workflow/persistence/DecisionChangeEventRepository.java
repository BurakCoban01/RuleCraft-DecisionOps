package com.rulecraft.decisionops.workflow.persistence;
import com.rulecraft.decisionops.workflow.domain.DecisionChangeEvent;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface DecisionChangeEventRepository extends JpaRepository<DecisionChangeEvent,UUID>{List<DecisionChangeEvent> findByRequestIdOrderByCreatedAtAsc(UUID requestId);Optional<DecisionChangeEvent> findByRequestIdAndClientRequestId(UUID requestId,UUID clientRequestId);}

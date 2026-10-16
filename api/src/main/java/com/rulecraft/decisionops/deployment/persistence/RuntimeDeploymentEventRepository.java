package com.rulecraft.decisionops.deployment.persistence;
import com.rulecraft.decisionops.deployment.domain.RuntimeDeploymentEvent;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface RuntimeDeploymentEventRepository extends JpaRepository<RuntimeDeploymentEvent,UUID>{List<RuntimeDeploymentEvent> findAllByDeploymentIdOrderByCreatedAtAsc(UUID deploymentId);}

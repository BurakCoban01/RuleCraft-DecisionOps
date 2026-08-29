package com.rulecraft.decisionops.explainability.persistence;

import com.rulecraft.decisionops.explainability.domain.DecisionTrace;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DecisionTraceRepository extends JpaRepository<DecisionTrace, UUID> {
    List<DecisionTrace> findTop100ByTenantIdAndProjectIdAndServiceIdOrderByCreatedAtDesc(
            UUID tenantId, UUID projectId, UUID serviceId);

    Optional<DecisionTrace> findByEvaluationId(UUID evaluationId);

    @EntityGraph(attributePaths = "steps")
    @Query("select trace from DecisionTrace trace where trace.evaluationId=:evaluationId and trace.tenantId=:tenantId and trace.projectId=:projectId and trace.serviceId=:serviceId")
    Optional<DecisionTrace> findScoped(@Param("evaluationId") UUID evaluationId,
            @Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId,
            @Param("serviceId") UUID serviceId);

    @EntityGraph(attributePaths = "steps")
    @Query("select trace from DecisionTrace trace where trace.evaluationId=:evaluationId and trace.tenantId=:tenantId and trace.serviceId=:serviceId and trace.credentialId=:credentialId")
    Optional<DecisionTrace> findForCredential(@Param("evaluationId") UUID evaluationId,
            @Param("tenantId") UUID tenantId, @Param("serviceId") UUID serviceId,
            @Param("credentialId") UUID credentialId);
}

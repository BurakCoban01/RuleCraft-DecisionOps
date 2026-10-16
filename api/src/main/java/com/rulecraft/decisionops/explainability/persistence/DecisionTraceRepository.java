package com.rulecraft.decisionops.explainability.persistence;

import com.rulecraft.decisionops.explainability.domain.DecisionTrace;
import java.util.*;
import java.time.Instant;
import com.rulecraft.decisionops.explainability.domain.TraceChannel;
import org.springframework.data.domain.Pageable;
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

    @Query("select trace from DecisionTrace trace where trace.tenantId=:tenantId and trace.projectId=:projectId and trace.serviceId=:serviceId and trace.createdAt>=:since and trace.createdAt<=:until and (:channel is null or trace.channel=:channel) order by trace.createdAt desc")
    List<DecisionTrace> findAnalytics(@Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId, @Param("serviceId") UUID serviceId,
            @Param("since") Instant since, @Param("until") Instant until,
            @Param("channel") TraceChannel channel, Pageable pageable);

    @Query("select count(trace) from DecisionTrace trace where trace.tenantId=:tenantId and trace.projectId=:projectId and trace.serviceId=:serviceId and trace.createdAt>=:since and trace.createdAt<=:until and (:channel is null or trace.channel=:channel)")
    long countAnalytics(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId,
            @Param("serviceId") UUID serviceId, @Param("since") Instant since, @Param("until") Instant until,
            @Param("channel") TraceChannel channel);
}

package com.rulecraft.decisionops.impact.persistence;

import com.rulecraft.decisionops.impact.domain.ImpactAssessment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpactAssessmentRepository extends JpaRepository<ImpactAssessment, UUID> {
    List<ImpactAssessment> findTop100ByTenantIdAndProjectIdAndServiceIdOrderByCapturedAtDesc(
            UUID tenantId, UUID projectId, UUID serviceId);
    Optional<ImpactAssessment> findByIdAndTenantIdAndProjectIdAndServiceId(
            UUID id, UUID tenantId, UUID projectId, UUID serviceId);
    Optional<ImpactAssessment> findByTenantIdAndServiceIdAndClientRequestId(
            UUID tenantId, UUID serviceId, UUID clientRequestId);
    long countByTenantIdAndServiceId(UUID tenantId, UUID serviceId);
}

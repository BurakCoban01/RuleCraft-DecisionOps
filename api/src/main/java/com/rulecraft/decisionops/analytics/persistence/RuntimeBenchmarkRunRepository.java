package com.rulecraft.decisionops.analytics.persistence;

import com.rulecraft.decisionops.analytics.domain.RuntimeBenchmarkRun;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeBenchmarkRunRepository extends JpaRepository<RuntimeBenchmarkRun, UUID> {
    List<RuntimeBenchmarkRun> findTop50ByTenantIdAndProjectIdAndServiceIdOrderByCapturedAtDesc(
            UUID tenantId, UUID projectId, UUID serviceId);
    Optional<RuntimeBenchmarkRun> findByTenantIdAndServiceIdAndClientRequestId(
            UUID tenantId, UUID serviceId, UUID clientRequestId);
    long countByTenantIdAndServiceId(UUID tenantId, UUID serviceId);
}

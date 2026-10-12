package com.rulecraft.decisionops.operations.persistence;

import com.rulecraft.decisionops.operations.domain.OperationalRecoveryRun;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalRecoveryRunRepository extends JpaRepository<OperationalRecoveryRun, UUID> {
    List<OperationalRecoveryRun> findTop25ByTenantIdOrderByExecutedAtDesc(UUID tenantId);
    Optional<OperationalRecoveryRun> findByTenantIdAndClientRequestId(UUID tenantId, UUID clientRequestId);
    long countByTenantId(UUID tenantId);
}

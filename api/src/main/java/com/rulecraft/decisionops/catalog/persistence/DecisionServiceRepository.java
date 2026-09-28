package com.rulecraft.decisionops.catalog.persistence;

import com.rulecraft.decisionops.catalog.domain.DecisionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface DecisionServiceRepository extends JpaRepository<DecisionService,UUID> {
    List<DecisionService> findAllByTenantIdAndProjectIdOrderByNameAsc(UUID tenantId,UUID projectId);
    Optional<DecisionService> findByIdAndTenantIdAndProjectId(UUID id,UUID tenantId,UUID projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select service from DecisionService service where service.id=:id and service.tenantId=:tenantId and service.projectId=:projectId")
    Optional<DecisionService> lockForVersionCapture(UUID id,UUID tenantId,UUID projectId);
    boolean existsByProjectIdAndServiceKey(UUID projectId,String serviceKey);
}

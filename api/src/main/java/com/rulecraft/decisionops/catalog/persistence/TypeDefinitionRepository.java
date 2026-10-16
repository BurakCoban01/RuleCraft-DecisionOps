package com.rulecraft.decisionops.catalog.persistence;

import com.rulecraft.decisionops.catalog.domain.TypeDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeDefinitionRepository extends JpaRepository<TypeDefinition,UUID> {
    List<TypeDefinition> findAllByTenantIdAndProjectIdOrderByNameAsc(UUID tenantId,UUID projectId);
    Optional<TypeDefinition> findByIdAndTenantIdAndProjectId(UUID id,UUID tenantId,UUID projectId);
    boolean existsByProjectIdAndTypeKey(UUID projectId,String typeKey);
    long countByItemCustomTypeId(UUID customTypeId);
}

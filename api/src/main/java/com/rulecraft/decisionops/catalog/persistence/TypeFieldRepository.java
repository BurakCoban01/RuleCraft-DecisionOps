package com.rulecraft.decisionops.catalog.persistence;

import com.rulecraft.decisionops.catalog.domain.TypeField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeFieldRepository extends JpaRepository<TypeField,UUID> {
    List<TypeField> findAllByTypeDefinitionIdOrderByPositionAsc(UUID typeDefinitionId);
    long countByCustomTypeId(UUID customTypeId);
    void deleteAllByTypeDefinitionId(UUID typeDefinitionId);
}

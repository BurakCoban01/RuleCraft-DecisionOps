package com.rulecraft.decisionops.catalog.persistence;

import com.rulecraft.decisionops.catalog.domain.TypeEnumValue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeEnumValueRepository extends JpaRepository<TypeEnumValue,UUID> {
    List<TypeEnumValue> findAllByTypeDefinitionIdOrderByPositionAsc(UUID typeDefinitionId);
    void deleteAllByTypeDefinitionId(UUID typeDefinitionId);
}

package com.rulecraft.decisionops.tables.persistence;

import com.rulecraft.decisionops.tables.domain.DecisionTableInputEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionTableEntryRepository extends JpaRepository<DecisionTableInputEntry,UUID> {
    List<DecisionTableInputEntry> findAllByDecisionTableId(UUID tableId);
    void deleteAllByDecisionTableId(UUID tableId);
}

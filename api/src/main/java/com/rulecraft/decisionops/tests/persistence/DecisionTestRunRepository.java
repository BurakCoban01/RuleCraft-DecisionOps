package com.rulecraft.decisionops.tests.persistence;
import com.rulecraft.decisionops.tests.domain.DecisionTestRun;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface DecisionTestRunRepository extends JpaRepository<DecisionTestRun,UUID>{List<DecisionTestRun> findTop20ByTestCaseIdAndTenantIdOrderByExecutedAtDesc(UUID testCaseId,UUID tenantId);Optional<DecisionTestRun> findFirstByTestCaseIdAndTenantIdOrderByExecutedAtDesc(UUID testCaseId,UUID tenantId);}

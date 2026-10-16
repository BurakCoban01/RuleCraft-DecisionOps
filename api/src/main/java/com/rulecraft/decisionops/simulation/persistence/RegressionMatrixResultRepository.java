package com.rulecraft.decisionops.simulation.persistence;
import com.rulecraft.decisionops.simulation.domain.RegressionMatrixResult;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface RegressionMatrixResultRepository extends JpaRepository<RegressionMatrixResult,UUID>{List<RegressionMatrixResult> findAllByMatrixRunIdOrderByPositionAsc(UUID matrixRunId);}

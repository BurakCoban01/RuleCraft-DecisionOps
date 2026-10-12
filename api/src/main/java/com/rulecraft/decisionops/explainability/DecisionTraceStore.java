package com.rulecraft.decisionops.explainability;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.explainability.domain.DecisionTrace;
import com.rulecraft.decisionops.explainability.persistence.DecisionTraceRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionTraceStore {
    private final DecisionTraceRepository traces;
    private final AuditService audit;

    public DecisionTraceStore(DecisionTraceRepository traces, AuditService audit) {
        this.traces = traces;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(List<DecisionTrace> values) {
        traces.saveAllAndFlush(values);
        values.forEach(value -> {
            var details = new LinkedHashMap<String, Object>();
            details.put("serviceId", value.getServiceId());
            details.put("deploymentId", value.getDeploymentId());
            details.put("decisionVersionId", value.getDecisionVersionId());
            details.put("channel", value.getChannel());
            details.put("status", value.getStatus());
            if (value.getClientRequestId() != null) details.put("requestId", value.getClientRequestId());
            if (value.getCorrelationId() != null) details.put("correlationId", value.getCorrelationId());
            audit.record(value.getTenantId(), value.getActorUserId(), "DECISION_TRACE_RECORDED",
                    "DECISION_TRACE", value.getEvaluationId(), details);
        });
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<DecisionTrace> load(UUID evaluationId) {
        return traces.findByEvaluationId(evaluationId);
    }
}

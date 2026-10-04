package com.rulecraft.decisionops.decisionapi;

import com.rulecraft.decisionops.decisionapi.domain.DecisionApiRequest;
import com.rulecraft.decisionops.decisionapi.persistence.DecisionApiRequestRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionApiEvidenceStore {
    private final DecisionApiRequestRepository requests;

    public DecisionApiEvidenceStore(DecisionApiRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(DecisionApiRequest request) {
        requests.saveAndFlush(request);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<DecisionApiRequest> load(UUID tenantId, UUID serviceId, String subject, UUID requestId) {
        return requests.findByTenantIdAndServiceIdAndSubjectKeyAndClientRequestId(
                tenantId, serviceId, subject, requestId);
    }
}

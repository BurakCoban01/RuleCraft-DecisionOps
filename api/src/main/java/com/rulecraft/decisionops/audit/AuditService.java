package com.rulecraft.decisionops.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.rulecraft.decisionops.audit.domain.AuditEvent;
import com.rulecraft.decisionops.audit.persistence.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(UUID tenantId, UUID actorUserId, String action, String targetType,
            Object targetId, Map<String, ?> details) {
        try {
            repository.save(new AuditEvent(tenantId, actorUserId, action, targetType,
                    String.valueOf(targetId), objectMapper.writeValueAsString(details), Instant.now(clock)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Audit details could not be serialized", exception);
        }
    }
}

package com.rulecraft.decisionops.audit.persistence;

import com.rulecraft.decisionops.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}

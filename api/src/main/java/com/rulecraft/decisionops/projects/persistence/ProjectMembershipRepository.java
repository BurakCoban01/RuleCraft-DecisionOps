package com.rulecraft.decisionops.projects.persistence;

import com.rulecraft.decisionops.projects.domain.ProjectMembership;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);
    Optional<ProjectMembership> findByTenantIdAndProjectIdAndUserId(UUID tenantId, UUID projectId, UUID userId);
    List<ProjectMembership> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);
    long countByProjectIdAndRole(UUID projectId, ProjectRole role);
}

package com.rulecraft.decisionops.projects;

import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.domain.Project;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.projects.persistence.ProjectMembershipRepository;
import com.rulecraft.decisionops.projects.persistence.ProjectRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccessPolicy {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;

    public ProjectAccessPolicy(ProjectRepository projects, ProjectMembershipRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
    }

    public Project requireVisible(RuleCraftPrincipal actor, UUID projectId) {
        var project = requireTenantProject(actor, projectId);
        if (actor.tenantRole() != TenantRole.ADMIN && membershipRole(actor, projectId) == null) notFound();
        return project;
    }

    public Project requireEdit(RuleCraftPrincipal actor, UUID projectId) {
        var project = requireVisible(actor, projectId);
        if (actor.tenantRole() == TenantRole.ADMIN) return project;
        var role = membershipRole(actor, projectId);
        if (role != ProjectRole.OWNER && role != ProjectRole.EDITOR) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-PROJECT-EDIT-FORBIDDEN",
                    "Project editor permission is required.");
        }
        return project;
    }

    public Project requireReview(RuleCraftPrincipal actor, UUID projectId) {
        var project = requireVisible(actor, projectId);
        if (actor.tenantRole() == TenantRole.ADMIN) return project;
        var role = membershipRole(actor, projectId);
        if (role != ProjectRole.OWNER && role != ProjectRole.REVIEWER) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-PROJECT-REVIEW-FORBIDDEN",
                    "Project reviewer permission is required.");
        }
        return project;
    }

    public Project requirePromote(RuleCraftPrincipal actor, UUID projectId) {
        var project = requireVisible(actor, projectId);
        if (actor.tenantRole() == TenantRole.ADMIN) return project;
        if (membershipRole(actor, projectId) != ProjectRole.OWNER) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-PROJECT-PROMOTE-FORBIDDEN",
                    "Project owner permission is required.");
        }
        return project;
    }

    public Project lockForManage(RuleCraftPrincipal actor, UUID projectId) {
        var project = projects.lockByIdAndTenantId(projectId, actor.tenantId()).orElseGet(() -> notFound());
        if (actor.tenantRole() == TenantRole.ADMIN) return project;
        if (membershipRole(actor, projectId) != ProjectRole.OWNER) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-PROJECT-MANAGE-FORBIDDEN",
                    "Project owner permission is required.");
        }
        return project;
    }

    public String roleFor(RuleCraftPrincipal actor, UUID projectId) {
        var role = membershipRole(actor, projectId);
        return role == null ? "ADMIN" : role.name();
    }

    private Project requireTenantProject(RuleCraftPrincipal actor, UUID projectId) {
        return projects.findByIdAndTenantId(projectId, actor.tenantId()).orElseGet(() -> notFound());
    }

    private ProjectRole membershipRole(RuleCraftPrincipal actor, UUID projectId) {
        return memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), projectId, actor.userId())
                .map(value -> value.getRole()).orElse(null);
    }

    private <T> T notFound() {
        throw new DomainProblemException(HttpStatus.NOT_FOUND, "RC-PROJECT-NOT-FOUND", "Project not found.");
    }
}

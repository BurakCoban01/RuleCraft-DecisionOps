package com.rulecraft.decisionops.projects;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.domain.Project;
import com.rulecraft.decisionops.projects.domain.ProjectMembership;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import com.rulecraft.decisionops.projects.persistence.ProjectMembershipRepository;
import com.rulecraft.decisionops.projects.persistence.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships,
            UserAccountRepository users, AuditService audit, Clock clock) {
        this.projects = projects;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(RuleCraftPrincipal actor) {
        var visible = actor.tenantRole() == TenantRole.ADMIN
                ? projects.findAllByTenantIdOrderByNameAsc(actor.tenantId())
                : projects.findVisibleToMember(actor.tenantId(), actor.userId());
        return visible.stream().map(project -> viewFor(project, actor)).toList();
    }

    @Transactional
    public ProjectView create(RuleCraftPrincipal actor, String projectKey, String name, String description) {
        var normalizedKey = projectKey.strip().toUpperCase(Locale.ROOT);
        if (projects.existsByTenantIdAndProjectKey(actor.tenantId(), normalizedKey)) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-PROJECT-KEY-EXISTS",
                    "That project key is already in use in this workspace.");
        }
        var now = Instant.now(clock);
        var project = projects.save(new Project(actor.tenantId(), normalizedKey, name, description,
                actor.userId(), now));
        memberships.save(new ProjectMembership(actor.tenantId(), project.getId(), actor.userId(),
                ProjectRole.OWNER, actor.userId(), now));
        audit.record(actor.tenantId(), actor.userId(), "PROJECT_CREATED", "PROJECT", project.getId(),
                Map.of("projectKey", normalizedKey));
        return new ProjectView(project.getId(), project.getProjectKey(), project.getName(), project.getDescription(),
                ProjectRole.OWNER.name(), project.getCreatedAt(), project.getVersion());
    }

    @Transactional(readOnly = true)
    public ProjectView get(RuleCraftPrincipal actor, UUID projectId) {
        var project = ownedProject(actor, projectId);
        requireVisible(actor, projectId);
        return viewFor(project, actor);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(RuleCraftPrincipal actor, UUID projectId) {
        ownedProject(actor, projectId);
        requireVisible(actor, projectId);
        return memberships.findAllByProjectIdOrderByCreatedAtAsc(projectId).stream().map(membership -> {
            var user = users.findByIdAndTenantId(membership.getUserId(), actor.tenantId()).orElseThrow();
            return new MemberView(user.getId(), user.getDisplayName(), user.getEmail(), membership.getRole().name());
        }).toList();
    }

    @Transactional
    public MemberView upsertMember(RuleCraftPrincipal actor, UUID projectId, UUID userId, ProjectRole role) {
        lockOwnedProject(actor, projectId);
        requireManage(actor, projectId);
        var user = users.findByIdAndTenantId(userId, actor.tenantId()).orElseThrow(() ->
                new DomainProblemException(HttpStatus.NOT_FOUND, "RC-USER-NOT-FOUND", "User not found."));
        var existing = memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), projectId, userId);
        if (existing.isPresent() && existing.get().getRole() == ProjectRole.OWNER && role != ProjectRole.OWNER
                && memberships.countByProjectIdAndRole(projectId, ProjectRole.OWNER) == 1) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-PROJECT-LAST-OWNER",
                    "Assign another owner before changing the final owner's role.");
        }
        var membership = existing.orElseGet(() -> new ProjectMembership(actor.tenantId(), projectId, userId,
                role, actor.userId(), Instant.now(clock)));
        membership.changeRole(role);
        memberships.save(membership);
        audit.record(actor.tenantId(), actor.userId(), "PROJECT_MEMBER_UPSERTED", "PROJECT", projectId,
                Map.of("userId", userId, "role", role.name()));
        return new MemberView(user.getId(), user.getDisplayName(), user.getEmail(), role.name());
    }

    @Transactional
    public void removeMember(RuleCraftPrincipal actor, UUID projectId, UUID userId) {
        lockOwnedProject(actor, projectId);
        requireManage(actor, projectId);
        var membership = memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), projectId, userId)
                .orElseThrow(() -> new DomainProblemException(HttpStatus.NOT_FOUND, "RC-MEMBER-NOT-FOUND",
                        "Project membership not found."));
        if (membership.getRole() == ProjectRole.OWNER
                && memberships.countByProjectIdAndRole(projectId, ProjectRole.OWNER) == 1) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-PROJECT-LAST-OWNER",
                    "Assign another owner before removing the final owner.");
        }
        memberships.delete(membership);
        audit.record(actor.tenantId(), actor.userId(), "PROJECT_MEMBER_REMOVED", "PROJECT", projectId,
                Map.of("userId", userId));
    }

    private Project ownedProject(RuleCraftPrincipal actor, UUID projectId) {
        return projects.findByIdAndTenantId(projectId, actor.tenantId()).orElseThrow(() ->
                new DomainProblemException(HttpStatus.NOT_FOUND, "RC-PROJECT-NOT-FOUND", "Project not found."));
    }

    private Project lockOwnedProject(RuleCraftPrincipal actor, UUID projectId) {
        return projects.lockByIdAndTenantId(projectId, actor.tenantId()).orElseThrow(() ->
                new DomainProblemException(HttpStatus.NOT_FOUND, "RC-PROJECT-NOT-FOUND", "Project not found."));
    }

    private void requireVisible(RuleCraftPrincipal actor, UUID projectId) {
        if (actor.tenantRole() != TenantRole.ADMIN
                && memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), projectId, actor.userId()).isEmpty()) {
            throw new DomainProblemException(HttpStatus.NOT_FOUND, "RC-PROJECT-NOT-FOUND", "Project not found.");
        }
    }

    private void requireManage(RuleCraftPrincipal actor, UUID projectId) {
        if (actor.tenantRole() == TenantRole.ADMIN) return;
        var membership = memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), projectId, actor.userId());
        if (membership.isEmpty() || membership.get().getRole() != ProjectRole.OWNER) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-PROJECT-MANAGE-FORBIDDEN",
                    "Project owner permission is required.");
        }
    }

    private ProjectView viewFor(Project project, RuleCraftPrincipal actor) {
        var role = memberships.findByTenantIdAndProjectIdAndUserId(actor.tenantId(), project.getId(), actor.userId())
                .map(value -> value.getRole().name()).orElse("ADMIN");
        return new ProjectView(project.getId(), project.getProjectKey(), project.getName(), project.getDescription(),
                role, project.getCreatedAt(), project.getVersion());
    }

    public record ProjectView(UUID id, String projectKey, String name, String description,
            String accessRole, Instant createdAt, long version) {}
    public record MemberView(UUID userId, String displayName, String email, String role) {}
}

package com.rulecraft.decisionops.projects.api;

import com.rulecraft.decisionops.identity.security.CurrentUser;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.ProjectService.MemberView;
import com.rulecraft.decisionops.projects.ProjectService.ProjectView;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }

    @GetMapping List<ProjectView> list(Authentication authentication) {
        return service.list(CurrentUser.require(authentication));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ProjectView create(@Valid @RequestBody CreateProjectRequest body, Authentication authentication) {
        return service.create(CurrentUser.require(authentication), body.projectKey(), body.name(), body.description());
    }

    @GetMapping("/{projectId}")
    ProjectView get(@PathVariable UUID projectId, Authentication authentication) {
        return service.get(CurrentUser.require(authentication), projectId);
    }

    @GetMapping("/{projectId}/members")
    List<MemberView> members(@PathVariable UUID projectId, Authentication authentication) {
        return service.members(CurrentUser.require(authentication), projectId);
    }

    @PutMapping("/{projectId}/members/{userId}")
    MemberView upsert(@PathVariable UUID projectId, @PathVariable UUID userId,
            @Valid @RequestBody MembershipRequest body, Authentication authentication) {
        return service.upsertMember(CurrentUser.require(authentication), projectId, userId, body.role());
    }

    @DeleteMapping("/{projectId}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID projectId, @PathVariable UUID userId, Authentication authentication) {
        service.removeMember(CurrentUser.require(authentication), projectId, userId);
    }

    public record CreateProjectRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9-]{1,23}") String projectKey,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 600) String description) {}
    public record MembershipRequest(@NotNull ProjectRole role) {}
}

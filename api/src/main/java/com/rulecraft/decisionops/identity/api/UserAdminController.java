package com.rulecraft.decisionops.identity.api;

import com.rulecraft.decisionops.identity.IdentityAdminService;
import com.rulecraft.decisionops.identity.IdentityAdminService.UserView;
import com.rulecraft.decisionops.identity.domain.AccountStatus;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {
    private final IdentityAdminService service;
    public UserAdminController(IdentityAdminService service) { this.service = service; }

    @GetMapping List<UserView> list(Authentication authentication) {
        return service.list(CurrentUser.require(authentication));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    UserView create(@Valid @RequestBody CreateUserRequest body, Authentication authentication) {
        return service.create(CurrentUser.require(authentication), body.email(), body.displayName(),
                body.tenantRole(), body.temporaryPassword());
    }

    @PatchMapping("/{userId}/status")
    UserView changeStatus(@PathVariable UUID userId, @Valid @RequestBody StatusRequest body,
            Authentication authentication) {
        return service.changeStatus(CurrentUser.require(authentication), userId, body.status());
    }

    @PostMapping("/{userId}/password-reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable UUID userId, @Valid @RequestBody ResetPasswordRequest body,
            Authentication authentication) {
        service.resetPassword(CurrentUser.require(authentication), userId, body.temporaryPassword());
    }

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName, @NotNull TenantRole tenantRole,
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword) {}
    public record StatusRequest(@NotNull AccountStatus status) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String temporaryPassword) {}
}

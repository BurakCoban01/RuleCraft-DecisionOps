package com.rulecraft.decisionops.identity;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.identity.domain.AccountStatus;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAdminService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public IdentityAdminService(UserAccountRepository users, PasswordEncoder passwordEncoder,
            AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserView> list(RuleCraftPrincipal actor) {
        requireAdmin(actor);
        return users.findAllByTenantIdOrderByDisplayNameAsc(actor.tenantId()).stream().map(UserView::from).toList();
    }

    @Transactional
    public UserView create(RuleCraftPrincipal actor, String email, String displayName,
            TenantRole role, String temporaryPassword) {
        requireAdmin(actor);
        var normalized = UserAccount.normalizeEmail(email);
        if (users.existsByTenantIdAndEmail(actor.tenantId(), normalized)) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-USER-EMAIL-EXISTS",
                    "An account with that email already exists in this workspace.");
        }
        var account = users.save(new UserAccount(actor.tenantId(), normalized, displayName,
                passwordEncoder.encode(temporaryPassword), role, Instant.now(clock)));
        audit.record(actor.tenantId(), actor.userId(), "USER_CREATED", "USER", account.getId(),
                Map.of("role", role.name()));
        return UserView.from(account);
    }

    @Transactional
    public UserView changeStatus(RuleCraftPrincipal actor, UUID userId, AccountStatus status) {
        requireAdmin(actor);
        if (actor.userId().equals(userId) && status == AccountStatus.DISABLED) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-USER-SELF-DISABLE",
                    "You cannot disable your own account.");
        }
        var account = ownedUser(actor, userId);
        if (account.getTenantRole() == TenantRole.ADMIN && status == AccountStatus.DISABLED
                && users.countByTenantIdAndTenantRoleAndStatus(actor.tenantId(), TenantRole.ADMIN, AccountStatus.ACTIVE) == 1) {
            throw new DomainProblemException(HttpStatus.CONFLICT, "RC-USER-LAST-ADMIN",
                    "Assign another active administrator before disabling the final administrator.");
        }
        account.changeStatus(status, Instant.now(clock));
        audit.record(actor.tenantId(), actor.userId(), "USER_STATUS_CHANGED", "USER", userId,
                Map.of("status", status.name()));
        return UserView.from(account);
    }

    @Transactional
    public void resetPassword(RuleCraftPrincipal actor, UUID userId, String temporaryPassword) {
        requireAdmin(actor);
        var account = ownedUser(actor, userId);
        account.changePassword(passwordEncoder.encode(temporaryPassword), Instant.now(clock));
        audit.record(actor.tenantId(), actor.userId(), "USER_PASSWORD_RESET", "USER", userId, Map.of());
    }

    private UserAccount ownedUser(RuleCraftPrincipal actor, UUID userId) {
        return users.findByIdAndTenantId(userId, actor.tenantId()).orElseThrow(() ->
                new DomainProblemException(HttpStatus.NOT_FOUND, "RC-USER-NOT-FOUND", "User not found."));
    }

    private void requireAdmin(RuleCraftPrincipal actor) {
        if (actor.tenantRole() != TenantRole.ADMIN) {
            throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-AUTH-FORBIDDEN",
                    "Workspace administration permission is required.");
        }
    }

    public record UserView(UUID id, String email, String displayName, String tenantRole, String status, long version) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getTenantRole().name(), user.getStatus().name(), user.getVersion());
        }
    }
}

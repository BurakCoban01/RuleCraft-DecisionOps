package com.rulecraft.decisionops.identity;

import com.rulecraft.decisionops.identity.domain.Tenant;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.TenantRepository;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityBootstrap implements ApplicationRunner {
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    @Value("${rulecraft.bootstrap.enabled:false}") private boolean enabled;
    @Value("${rulecraft.bootstrap.workspace:}") private String workspace;
    @Value("${rulecraft.bootstrap.tenant-name:}") private String tenantName;
    @Value("${rulecraft.bootstrap.admin-email:}") private String email;
    @Value("${rulecraft.bootstrap.admin-name:}") private String displayName;
    @Value("${rulecraft.bootstrap.admin-password:}") private String password;

    public IdentityBootstrap(TenantRepository tenants, UserAccountRepository users,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!workspace.matches("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") || tenantName.isBlank()
                || !email.contains("@") || displayName.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Enabled identity bootstrap requires a valid workspace, tenant, admin, and 12+ character password");
        }
        var now = Instant.now(clock);
        var tenant = tenants.findBySlug(workspace).orElseGet(() -> tenants.save(new Tenant(workspace, tenantName, now)));
        if (!users.existsByTenantIdAndEmail(tenant.getId(), UserAccount.normalizeEmail(email))) {
            users.save(new UserAccount(tenant.getId(), email, displayName, passwordEncoder.encode(password),
                    TenantRole.ADMIN, now));
        }
    }
}

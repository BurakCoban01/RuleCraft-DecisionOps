package com.rulecraft.decisionops.identity.security;

import com.rulecraft.decisionops.identity.domain.AccountStatus;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class RuleCraftPrincipal implements UserDetails {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID userId;
    private final UUID tenantId;
    private final String tenantSlug;
    private final String displayName;
    private final String email;
    private final transient String passwordHash;
    private final TenantRole tenantRole;
    private final AccountStatus status;

    public RuleCraftPrincipal(UserAccount account, String tenantSlug) {
        this.userId = account.getId();
        this.tenantId = account.getTenantId();
        this.tenantSlug = tenantSlug;
        this.displayName = account.getDisplayName();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.tenantRole = account.getTenantRole();
        this.status = account.getStatus();
    }

    public UUID userId() { return userId; }
    public UUID tenantId() { return tenantId; }
    public String tenantSlug() { return tenantSlug; }
    public String displayName() { return displayName; }
    public String email() { return email; }
    public TenantRole tenantRole() { return tenantRole; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + tenantRole.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return tenantSlug + ":" + email; }
    @Override public boolean isEnabled() { return status == AccountStatus.ACTIVE; }
}

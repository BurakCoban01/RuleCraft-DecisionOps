package com.rulecraft.decisionops.identity.security;

import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.TenantRepository;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RuleCraftUserDetailsService implements UserDetailsService {
    public static final String SEPARATOR = "\u001f";
    private final UserAccountRepository users;
    private final TenantRepository tenants;

    public RuleCraftUserDetailsService(UserAccountRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    public UserDetails loadUserByUsername(String qualifiedUsername) {
        var parts = qualifiedUsername.split(SEPARATOR, 2);
        if (parts.length != 2) throw new UsernameNotFoundException("Invalid credentials");
        UserAccount account = users.findForLogin(parts[0].strip().toLowerCase(),
                        UserAccount.normalizeEmail(parts[1]))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var tenant = tenants.findById(account.getTenantId())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new RuleCraftPrincipal(account, tenant.getSlug());
    }
}

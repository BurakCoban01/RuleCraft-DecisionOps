package com.rulecraft.decisionops.identity.security;

import com.rulecraft.decisionops.identity.domain.AccountStatus;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserAccountRepository users;
    public ActiveAccountFilter(UserAccountRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof RuleCraftPrincipal principal) {
            var active = users.findByIdAndTenantId(principal.userId(), principal.tenantId())
                    .filter(user -> user.getStatus() == AccountStatus.ACTIVE).isPresent();
            if (!active) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(401);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"type\":\"urn:rulecraft:problem:rc-auth-disabled\","
                        + "\"title\":\"Account unavailable\",\"status\":401,"
                        + "\"detail\":\"This account is no longer active.\",\"code\":\"RC-AUTH-DISABLED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

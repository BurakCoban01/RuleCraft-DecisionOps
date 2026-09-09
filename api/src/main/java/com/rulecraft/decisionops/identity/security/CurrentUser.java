package com.rulecraft.decisionops.identity.security;

import com.rulecraft.decisionops.platform.web.DomainProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {}

    public static RuleCraftPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof RuleCraftPrincipal principal)) {
            throw new DomainProblemException(HttpStatus.UNAUTHORIZED, "RC-AUTH-REQUIRED", "Authentication is required.");
        }
        return principal;
    }
}

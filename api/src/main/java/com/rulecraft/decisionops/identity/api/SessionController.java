package com.rulecraft.decisionops.identity.api;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.identity.security.RuleCraftUserDetailsService;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.platform.security.AbuseProtectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AbuseProtectionService abuseProtection;
    private final Clock clock;

    public SessionController(AuthenticationManager authenticationManager, UserAccountRepository users,
            PasswordEncoder passwordEncoder, AuditService audit, AbuseProtectionService abuseProtection, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.abuseProtection = abuseProtection;
        this.clock = clock;
    }

    @GetMapping
    SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        if (authentication == null || !(authentication.getPrincipal() instanceof RuleCraftPrincipal principal)) {
            return SessionResponse.anonymous();
        }
        return SessionResponse.authenticated(principal);
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        var rateKey = abuseProtection.loginKey(body.workspace(), body.email(), request.getRemoteAddr());
        abuseProtection.requireLoginAllowed(rateKey);
        try {
            var token = UsernamePasswordAuthenticationToken.unauthenticated(
                    body.workspace().strip().toLowerCase(Locale.ROOT) + RuleCraftUserDetailsService.SEPARATOR + body.email(),
                    body.password());
            var authentication = authenticationManager.authenticate(token);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true);
            request.changeSessionId();
            contextRepository.saveContext(context, request, response);
            var principal = (RuleCraftPrincipal) authentication.getPrincipal();
            abuseProtection.loginSucceeded(rateKey);
            audit.record(principal.tenantId(), principal.userId(), "SESSION_LOGIN", "USER", principal.userId(), Map.of());
            return SessionResponse.authenticated(principal);
        } catch (org.springframework.security.core.AuthenticationException exception) {
            abuseProtection.loginFailed(rateKey);
            throw new DomainProblemException(HttpStatus.UNAUTHORIZED, "RC-AUTH-INVALID",
                    "The workspace, email, or password is incorrect.");
        }
    }

    @DeleteMapping
    void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (authentication != null && authentication.getPrincipal() instanceof RuleCraftPrincipal principal) {
            audit.record(principal.tenantId(), principal.userId(), "SESSION_LOGOUT", "USER", principal.userId(), Map.of());
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @PutMapping("/password")
    void changePassword(@Valid @RequestBody PasswordChangeRequest body, Authentication authentication) {
        var principal = requirePrincipal(authentication);
        UserAccount account = users.findByIdAndTenantId(principal.userId(), principal.tenantId()).orElseThrow();
        if (!passwordEncoder.matches(body.currentPassword(), account.getPasswordHash())) {
            throw new DomainProblemException(HttpStatus.BAD_REQUEST, "RC-PASSWORD-CURRENT",
                    "The current password is incorrect.");
        }
        account.changePassword(passwordEncoder.encode(body.newPassword()), Instant.now(clock));
        users.save(account);
        audit.record(principal.tenantId(), principal.userId(), "PASSWORD_CHANGED", "USER", principal.userId(), Map.of());
    }

    private RuleCraftPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof RuleCraftPrincipal principal)) {
            throw new DomainProblemException(HttpStatus.UNAUTHORIZED, "RC-AUTH-REQUIRED", "Authentication is required.");
        }
        return principal;
    }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}[a-z0-9]") String workspace,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    public record SessionResponse(boolean authenticated, UserSummary user) {
        static SessionResponse anonymous() { return new SessionResponse(false, null); }
        static SessionResponse authenticated(RuleCraftPrincipal principal) {
            return new SessionResponse(true, new UserSummary(principal.userId(), principal.tenantId(),
                    principal.tenantSlug(), principal.displayName(), principal.email(), principal.tenantRole().name()));
        }
    }

    public record UserSummary(java.util.UUID id, java.util.UUID tenantId, String workspace,
            String displayName, String email, String tenantRole) {}
}

package com.rulecraft.decisionops.platform.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }
}

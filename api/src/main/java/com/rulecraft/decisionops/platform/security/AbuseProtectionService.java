package com.rulecraft.decisionops.platform.security;

import com.rulecraft.decisionops.platform.web.DomainProblemException;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AbuseProtectionService {
    private static final int MAX_KEYS = 10_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong loginDenials = new AtomicLong();
    private final AtomicLong runtimeDenials = new AtomicLong();

    public AbuseProtectionService(Clock clock) { this.clock = clock; }

    public String loginKey(String workspace, String email, String remoteAddress) {
        return hash("login|" + normalized(workspace) + "|" + normalized(email) + "|" + remoteAddress);
    }
    public void requireLoginAllowed(String key) {
        var value = windows.get(key); var now = Instant.now(clock);
        if (value != null && value.blockedUntil != null && value.blockedUntil.isAfter(now)) {
            loginDenials.incrementAndGet();
            throw new DomainProblemException(HttpStatus.TOO_MANY_REQUESTS, "RC-AUTH-RATE-LIMIT",
                    "Too many failed sign-in attempts. Wait five minutes before trying again.");
        }
    }
    public void loginFailed(String key) {
        compact(); var now = Instant.now(clock);
        windows.compute(key, (ignored, value) -> {
            if (value == null || value.startedAt.plus(Duration.ofMinutes(5)).isBefore(now)) value = new Window(now);
            value.attempts++;
            if (value.attempts >= 5) value.blockedUntil = now.plus(Duration.ofMinutes(5));
            return value;
        });
    }
    public void loginSucceeded(String key) { windows.remove(key); }

    public boolean allowRuntime(String key, HttpServletResponse response) {
        compact(); var now = Instant.now(clock); var value = windows.compute("runtime|" + key, (ignored, current) -> {
            if (current == null || current.startedAt.plus(Duration.ofMinutes(1)).isBefore(now)) current = new Window(now);
            current.attempts++; return current;
        });
        var remaining = Math.max(0, 120 - value.attempts);
        response.setHeader("X-RateLimit-Limit", "120"); response.setHeader("X-RateLimit-Remaining", Integer.toString(remaining));
        if (value.attempts <= 120) return true;
        runtimeDenials.incrementAndGet(); response.setHeader("Retry-After", "60"); return false;
    }
    public long loginDenials() { return loginDenials.get(); }
    public long runtimeDenials() { return runtimeDenials.get(); }
    public int trackedKeys() { return windows.size(); }

    private void compact() {
        if (windows.size() < MAX_KEYS) return;
        var cutoff = Instant.now(clock).minus(Duration.ofMinutes(10));
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt.isBefore(cutoff));
        if (windows.size() >= MAX_KEYS) windows.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getValue().startedAt)).limit(1_000)
                .map(java.util.Map.Entry::getKey).toList().forEach(windows::remove);
    }
    private String normalized(String value) { return value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static final class Window { private final Instant startedAt; private int attempts; private Instant blockedUntil;
        private Window(Instant startedAt) { this.startedAt = startedAt; } }
}

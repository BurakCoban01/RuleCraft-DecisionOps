package com.rulecraft.decisionops.operations;

import com.rulecraft.decisionops.audit.AuditService;
import com.rulecraft.decisionops.deployment.domain.RuntimeDeploymentState;
import com.rulecraft.decisionops.deployment.persistence.*;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.persistence.TenantRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.operations.domain.OperationalRecoveryRun;
import com.rulecraft.decisionops.operations.persistence.OperationalRecoveryRunRepository;
import com.rulecraft.decisionops.platform.security.AbuseProtectionService;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.simulation.domain.MatrixRunStatus;
import com.rulecraft.decisionops.simulation.persistence.RegressionMatrixRunRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private static final String EXPECTED_SCHEMA = "16";
    private static final Instant STARTED_AT = Instant.now();
    private final OperationalRecoveryRunRepository recoveryRuns;
    private final RegressionMatrixRunRepository matrices;
    private final RuntimeEnvironmentRepository environments;
    private final RuntimeDeploymentRepository deployments;
    private final TenantRepository tenants;
    private final AbuseProtectionService abuseProtection;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OperationsService(OperationalRecoveryRunRepository recoveryRuns, RegressionMatrixRunRepository matrices,
            RuntimeEnvironmentRepository environments, RuntimeDeploymentRepository deployments,
            TenantRepository tenants, AbuseProtectionService abuseProtection, AuditService audit,
            JdbcTemplate jdbc, Clock clock) {
        this.recoveryRuns = recoveryRuns; this.matrices = matrices; this.environments = environments;
        this.deployments = deployments; this.tenants = tenants; this.abuseProtection = abuseProtection;
        this.audit = audit; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationsWorkspace workspace(RuleCraftPrincipal actor) {
        requireAdmin(actor); var posture = posture(actor.tenantId());
        var history = recoveryRuns.findTop25ByTenantIdOrderByExecutedAtDesc(actor.tenantId()).stream()
                .map(this::view).toList();
        return new OperationsWorkspace(posture, history,
                new SecurityPosture(true, true, true, true, 5, 120,
                        abuseProtection.loginDenials(), abuseProtection.runtimeDenials(), abuseProtection.trackedKeys()),
                Duration.between(STARTED_AT, Instant.now(clock)).toSeconds());
    }

    @Transactional
    public RecoveryView recover(RuleCraftPrincipal actor, RecoveryCommand command) {
        requireAdmin(actor);
        if (command == null || command.clientRequestId() == null) bad("RC-RECOVERY-REQUEST-ID",
                "Provide a request ID so recovery retries remain idempotent.");
        var retry = recoveryRuns.findByTenantIdAndClientRequestId(actor.tenantId(), command.clientRequestId());
        if (retry.isPresent()) return exactRetry(retry.get(), command.expectedFingerprint());
        tenants.lockById(actor.tenantId()).orElseThrow(() -> new DomainProblemException(HttpStatus.NOT_FOUND,
                "RC-RECOVERY-TENANT-NOT-FOUND", "Workspace not found."));
        retry = recoveryRuns.findByTenantIdAndClientRequestId(actor.tenantId(), command.clientRequestId());
        if (retry.isPresent()) return exactRetry(retry.get(), command.expectedFingerprint());
        if (recoveryRuns.countByTenantId(actor.tenantId()) >= 1_000) bad("RC-RECOVERY-LIMIT",
                "A workspace can retain at most 1,000 recovery runs.");
        var before = posture(actor.tenantId());
        if (command.expectedFingerprint() == null || !command.expectedFingerprint().equals(before.fingerprint())) {
            throw conflict("RC-RECOVERY-STALE", "Operational posture changed. Refresh before running recovery.");
        }
        var now = Instant.now(clock);
        var interrupted = matrices.interruptRunningByTenant(actor.tenantId(), now);
        var cleared = 0;
        for (var environment : environments.findAllByTenantId(actor.tenantId())) {
            if (environment.getActiveDeploymentId() != null && !validPointer(environment)) {
                environment.clear(now); environments.save(environment); cleared++;
            }
        }
        var after = posture(actor.tenantId());
        var run = recoveryRuns.saveAndFlush(new OperationalRecoveryRun(actor.tenantId(), command.clientRequestId(),
                before.fingerprint(), after.fingerprint(), interrupted, cleared, after.status(), actor.userId(),
                actor.displayName(), now));
        audit.record(actor.tenantId(), actor.userId(), "OPERATIONAL_RECOVERY_COMPLETED", "RECOVERY_RUN", run.getId(),
                Map.of("status", after.status(), "interruptedMatrices", interrupted,
                        "clearedRuntimePointers", cleared, "resultFingerprint", after.fingerprint()));
        return view(run);
    }

    private OperationalPosture posture(UUID tenantId) {
        var database = databaseAvailable(); var schema = database ? schemaVersion() : "unavailable";
        var running = matrices.countByTenantIdAndStatus(tenantId, MatrixRunStatus.RUNNING);
        var pointerIssues = environments.findAllByTenantId(tenantId).stream()
                .filter(value -> value.getActiveDeploymentId() != null && !validPointer(value)).count();
        var checks = List.of(
                new OperationalCheck("DATABASE", database ? "PASS" : "FAIL", database ? "Authoritative PostgreSQL query succeeded." : "Database query failed."),
                new OperationalCheck("SCHEMA", EXPECTED_SCHEMA.equals(schema) ? "PASS" : "FAIL", "Flyway schema version " + schema + "; expected " + EXPECTED_SCHEMA + "."),
                new OperationalCheck("REGRESSION_RECOVERY", running == 0 ? "PASS" : "WARN", running + " matrix runs remain RUNNING."),
                new OperationalCheck("RUNTIME_POINTERS", pointerIssues == 0 ? "PASS" : "WARN", pointerIssues + " active runtime pointers are inconsistent."));
        var status = checks.stream().allMatch(value -> "PASS".equals(value.status())) ? "READY" : "DEGRADED";
        var fingerprint = sha256(database + "|" + schema + "|" + running + "|" + pointerIssues + "|" + status);
        return new OperationalPosture(status, fingerprint, Instant.now(clock), schema, running, pointerIssues, checks);
    }
    private boolean validPointer(com.rulecraft.decisionops.deployment.domain.RuntimeEnvironment environment) {
        return deployments.findByIdAndServiceIdAndTenantIdAndProjectId(environment.getActiveDeploymentId(),
                environment.getServiceId(), environment.getTenantId(), environment.getProjectId())
                .filter(value -> value.getState() == RuntimeDeploymentState.ACTIVE).isPresent();
    }
    private boolean databaseAvailable() { try { return Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class)); }
        catch (RuntimeException failure) { return false; } }
    private String schemaVersion() { try { return jdbc.queryForObject(
            "select version from flyway_schema_history where success=true order by installed_rank desc fetch first 1 row only", String.class); }
        catch (RuntimeException failure) { return "unknown"; } }
    private RecoveryView exactRetry(OperationalRecoveryRun run, String expectedFingerprint) {
        if (expectedFingerprint == null || !MessageDigest.isEqual(run.getSourceFingerprint().getBytes(StandardCharsets.UTF_8),
                expectedFingerprint.getBytes(StandardCharsets.UTF_8))) throw conflict("RC-RECOVERY-REQUEST-CONFLICT",
                        "This request ID already recovered a different operational posture.");
        return view(run);
    }
    private RecoveryView view(OperationalRecoveryRun value) { return new RecoveryView(value.getId(),
            value.getSourceFingerprint(), value.getResultFingerprint(), value.getInterruptedMatrices(),
            value.getClearedRuntimePointers(), value.getStatus(), value.getExecutedByName(), value.getExecutedAt()); }
    private void requireAdmin(RuleCraftPrincipal actor) { if (actor.tenantRole() != TenantRole.ADMIN)
        throw new DomainProblemException(HttpStatus.FORBIDDEN, "RC-OPERATIONS-ADMIN-REQUIRED",
                "Workspace administrator access is required for operational controls."); }
    private void bad(String code, String detail) { throw new DomainProblemException(HttpStatus.BAD_REQUEST, code, detail); }
    private DomainProblemException conflict(String code, String detail) { return new DomainProblemException(HttpStatus.CONFLICT, code, detail); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }

    public record OperationsWorkspace(OperationalPosture posture, List<RecoveryView> recoveries,
            SecurityPosture security, long processUptimeSeconds) {}
    public record OperationalPosture(String status, String fingerprint, Instant checkedAt, String schemaVersion,
            long runningMatrices, long inconsistentRuntimePointers, List<OperationalCheck> checks) {}
    public record OperationalCheck(String key, String status, String detail) {}
    public record SecurityPosture(boolean csrfProtected, boolean secureHeaders, boolean apiKeysHashed,
            boolean correlationIds, int failedLoginLimit, int runtimeRequestsPerMinute,
            long loginDenials, long runtimeDenials, int trackedRateLimitKeys) {}
    public record RecoveryCommand(UUID clientRequestId, String expectedFingerprint) {}
    public record RecoveryView(UUID id, String sourceFingerprint, String resultFingerprint,
            int interruptedMatrices, int clearedRuntimePointers, String status,
            String executedByName, Instant executedAt) {}
}

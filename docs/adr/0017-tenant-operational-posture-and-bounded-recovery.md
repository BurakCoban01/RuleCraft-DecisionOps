# ADR 0017: Tenant operational posture and bounded recovery

## Status

Accepted — 2026-08-29

## Context

RuleCraft already has tenant isolation, governed immutable artifacts, durable regression work, runtime pointers, hashed Decision API credentials, and privacy-safe traces. Operators still need one authoritative place to verify the application’s recoverable state and repair the two local failure modes that can safely be corrected without rewriting governed evidence. Security controls also need visible, testable application boundaries without claiming that one JVM can provide cluster-wide traffic enforcement.

## Decision

- A tenant administrator can read an operational posture derived from an authoritative database query, the latest successful Flyway version, tenant-scoped running regression matrices, and tenant-scoped active runtime pointers. The posture is `READY` only when every check passes; its SHA-256 fingerprint canonically identifies the repair-relevant state.
- A recovery scan accepts a client request ID and the expected posture fingerprint. It serializes on the tenant row, rejects stale posture, converges exact retries, conflicts on changed request reuse, and retains at most 1,000 immutable runs per tenant.
- Recovery may only mark tenant-scoped `RUNNING` regression matrices `INTERRUPTED` and clear an active runtime pointer that no longer resolves to an exactly matching active tenant/project/service deployment. It never deletes or rewrites decision versions, traces, audits, deployment history, tests, approvals, or authored decision state.
- Recovery evidence stores source/result fingerprints, repaired counts, status, actor, and time. Audit metadata remains payload-free. The operations workspace returns the newest 25 runs.
- Browser sessions retain Spring Security CSRF and server-derived tenant identity. API responses receive no-store and restrictive referrer/capability/cross-origin headers. Login failure windows are keyed by a SHA-256 digest of normalized workspace, email, and remote address; five failures block that key for five minutes.
- Valid Decision API credentials are limited to 120 requests per minute per key in the current process and return explicit limit/remaining/retry headers. Keys remain persisted only as SHA-256 digests. In-memory windows are bounded to 10,000 keys with time-based compaction and oldest-window eviction.
- The operations UI exposes denial counters and tracked windows as process-local evidence. It explicitly requires deployment ingress to enforce a shared outer limit; the application does not claim distributed enforcement, durable counters, alerts, or a full telemetry system.

## Consequences

Administrators can distinguish authoritative readiness from recoverable degradation, perform a narrow idempotent repair, export posture evidence, and verify the result after reload. Existing governed history remains immutable and tenant isolation is preserved.

operational security does not add external observability infrastructure, distributed rate-limit coordination, automatic destructive repair, backup orchestration, alert routing, or deployment. container readiness owns deployment-ready configuration and operator packaging; release acceptance owns final acceptance.

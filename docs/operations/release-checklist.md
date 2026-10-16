# Release readiness checklist

## Evidence

- [ ] Intended commit and immutable build version recorded; worktree reviewed.
- [ ] API tests and package, web lint/tests/build, Compose render, exposure scan, and both image builds pass.
- [ ] Base-image and framework pins reverified from official primary sources.
- [ ] Image digests, sizes, non-root users, read-only roots, capability drops, and resource ceilings recorded.
- [ ] No real secret, `.env.production`, backup archive, test report, or provider token is tracked.

## Data and recovery

- [ ] Fresh checksummed PostgreSQL backup copied to encrypted off-host storage.
- [ ] Restore drill passed for the exact release family and expected database name.
- [ ] Migration compatibility and application rollback decision recorded.
- [ ] Operations posture and fingerprint captured before and after the change.

## Security and networking

- [ ] Only the approved TLS ingress is public; rendered Compose still publishes only loopback web.
- [ ] API, management, and PostgreSQL ports are private; actuator/OpenAPI return 404 at the edge.
- [ ] TLS certificate, HSTS ownership, trusted forwarded headers, 1 MB body cap, shared ingress rate limits, and access-log redaction are verified.
- [ ] Bootstrap is disabled and its password removed; production secrets come from the approved secret manager or protected host environment.
- [ ] Session cookie is Secure/HTTP-only/SameSite Strict and CSRF/login/API-key tests pass.

## Product acceptance

- [ ] Liveness/readiness, V16/current schema, login, Projects, Operations, approval/promotion, active runtime evaluation, Decision API single/batch, trace, analytics, and recovery states pass.
- [ ] Desktop and compact critical journeys have no console/network errors or horizontal overflow.
- [ ] Degraded dependency, stale/retry, permission, and recovery behavior remains actionable and non-disclosing.

## Authorization boundary

This checklist does not authorize deployment or public exposure. A human release owner must approve the target, maintenance window, ingress, secrets, backup retention, monitoring, and rollback before any provider-specific action.

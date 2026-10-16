# Security, observability, and recovery

RuleCraft provides tenant administrators with an Operations workspace at `/admin/operations`. It reports application posture from the database and schema actually serving the session, rather than from frontend constants or synthetic metrics.

## Readiness checks

- `DATABASE` verifies an authoritative database query.
- `SCHEMA` compares the latest successful Flyway version with the application’s expected version.
- `REGRESSION_RECOVERY` identifies tenant-owned matrix runs still marked `RUNNING`.
- `RUNTIME_POINTERS` identifies an environment whose active pointer does not resolve to an active deployment for the same tenant, project, and service.

`READY` means every check passes. Any failure or recoverable warning produces `DEGRADED`. The displayed fingerprint covers the schema and repair-relevant counts, so an administrator can export and compare evidence without exposing decision inputs, outputs, credentials, or session data.

## Recovery procedure

1. Open **Operations** as a workspace administrator and review every degraded check.
2. Export the current evidence if it is needed for an incident record.
3. Choose **Run recovery scan**. RuleCraft submits a fresh idempotency ID and the displayed fingerprint.
4. If the posture changed, refresh and inspect the new state before retrying.
5. Confirm the resulting `READY` or `DEGRADED` status and immutable recovery-history entry.

The scan interrupts tenant matrix runs marked `RUNNING` and clears invalid active runtime pointers. Operators must coordinate with active work first: the application does not maintain distributed worker leases. It preserves decision versions, deployment attempts and history, traces, audits, scenarios, approvals, and authoring data. Reusing the same request ID with the same source posture returns the original result; changed reuse is rejected. Recovery history is bounded to 1,000 records per tenant and the UI shows the newest 25.

## Security boundary

Authenticated browser writes remain CSRF protected and tenant identity is derived from the server-side session. API responses are non-cacheable and include restrictive referrer, browser-capability, opener, resource, and cross-domain policy headers. Decision API credentials remain digest-only at rest.

Five failed sign-in attempts for the same normalized workspace, email, and remote-address digest cause a five-minute block. An active Decision API key permits 120 requests per one-minute process window and receives limit, remaining, and retry metadata. The Operations workspace shows node-local denial counters and tracked windows, never credentials or request values.

These counters and windows intentionally live in one application process. A multi-replica deployment must add a shared ingress limit and external monitoring/alert routing. Process restart resets counters and windows; durable recovery evidence and audits remain in PostgreSQL.

## Failure handling

- A non-administrator receives a stable forbidden problem and cannot load recovery evidence.
- A missing request ID is rejected before mutation.
- A stale fingerprint returns a conflict; refresh before deciding whether recovery is still appropriate.
- A request ID reused for another posture returns a conflict.
- Login and runtime throttling return stable `429` problems without echoing credentials or request payloads.
- Database or schema failure degrades the reported check when the authenticated request can still reach the service. PostgreSQL availability and backup restoration remain operator responsibilities.

No external service is deployed by this design.

# ADR 0011: Governed approval and promotion baseline

## Status

Accepted — 2026-08-29

## Context

version review provides immutable decision versions but deliberately has no review state. The platform needs explicit author, reviewer, and owner responsibilities, durable decisions, and a single governed baseline before runtime sandbox introduces runtime environments. Approval must never drift onto a mutable working copy or silently promote an invalid or superseded candidate.

## Decision

- A change request binds exactly one immutable decision-version ID and retains a service-scoped sequence, title, summary, creator snapshot, optimistic revision, and append-only actor-named event timeline.
- The state machine is `DRAFT → SUBMITTED → APPROVED → PROMOTED`, with terminal `CHANGES_REQUESTED` and `REJECTED` review outcomes. Fixes require a new immutable version and change request; history is never rewritten.
- Owners and editors create and submit. Owners and reviewers approve, reject, or request changes. Only owners promote. Tenant administrators retain their established administrative override. Viewers may inspect evidence but cannot transition it.
- Create, submit, approve, and promote require the latest captured version to be `VALID`. A newer capture makes an older request visibly stale and prevents its approval or promotion.
- Every mutation is serialized with the service capture boundary, rejects archived services, checks an optimistic request revision, requires a client request UUID, and treats an exact retry idempotently. Reusing an operation UUID for another action is rejected.
- Change-request and event histories are capped at 1,000 service requests. Promotion updates one tenant/project/service-qualified governed-baseline pointer while the promoted request and event remain immutable history.
- `PROMOTED` is policy state, not runtime deployment. It does not activate endpoints, environments, or artifacts.

## Consequences

runtime sandbox can require an explicit promoted immutable baseline before creating a runtime deployment, while approval and promotion remains usable without infrastructure profiles or a worker. Actor, note, timestamp, transition, version, and audit evidence survive reload and later promotions.

Rollback, artifact activation, environment health, and captured execution use the runtime deployment boundary. Rollback creates an explicit new history event rather than mutating an earlier promotion.

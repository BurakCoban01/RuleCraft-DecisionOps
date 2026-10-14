# ADR 0018: Private container deployment contract

## Status

Accepted — 2026-08-29

## Context

RuleCraft must be fully deployment-ready without creating a provider resource, public endpoint, registry artifact, or hidden optional dependency. The application has a stateful PostgreSQL authority, a Spring Boot API, and a browser client that depends on same-origin session and CSRF behavior. Operators need immutable builds, explicit health, resource bounds, backup/restore, and a topology that is private by default.

## Decision

- Package the API and web client as separate OCI images from version-pinned Docker Official Images. Builds use the repository’s Maven and pnpm locks; runtime images contain no compiler toolchain or test sources.
- Run the API as UID/GID 10001 on a Temurin 25 JRE and the web edge as nginx’s unprivileged user on port 8080. Both application containers use read-only roots, bounded tmpfs, all-capability drops, no-new-privileges, graceful stop periods, health checks, and explicit memory/CPU ceilings.
- Keep PostgreSQL on an internal data network with a named durable volume and no published port. Keep the API off host ports. Publish only nginx, bound to `127.0.0.1` by default, so a separately managed TLS ingress can be the public boundary.
- nginx serves the SPA and proxies only `/api/`. It blocks actuator and OpenAPI routes, emits browser hardening headers, caps request bodies, preserves the trusted loopback proxy’s original scheme, and provides a container-local `/healthz`.
- Spring Boot’s production profile uses a separate internal management port for liveness/readiness, secure/HTTP-only/strict session cookies by default, forwarded-header processing, graceful shutdown, bounded database connections, UTC persistence, disabled OpenAPI, and non-disclosing health.
- A scanner renders the effective Compose model and fails if API/database ports are published, web is not loopback-bound, the data network is not internal, a service is privileged, or an application container loses its read-only/capability/no-new-privileges boundary.
- PostgreSQL backups use custom format plus a SHA-256 sidecar. Restore requires an explicit reset switch, exact database-name confirmation, a stopped API, optional checksum verification, and post-restore readiness/schema/sign-in/posture validation.
- CI performs the application verification, private-topology scan, Compose validation, and both container builds. It never pushes images or deploys.

## Consequences

The same repository can produce and locally validate immutable deployment artifacts while remaining private and provider-neutral. Default host exposure is fail-closed and recoverability is exercised against real PostgreSQL.

TLS termination, certificates, DNS, public ingress, registry signing/attestation storage, external secret management, off-host backup retention, monitoring/alerts, and provider rollout are operator/platform responsibilities. Vercel is not used because RuleCraft requires a stateful same-origin Java/session API and private PostgreSQL topology; splitting only the static client would weaken the verified deployment boundary.

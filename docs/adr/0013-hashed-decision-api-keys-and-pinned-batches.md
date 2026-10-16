# ADR 0013: Hashed decision API keys and version-pinned batches

## Status

Accepted — 2026-08-29

## Context

runtime sandbox provides an authenticated local runtime sandbox but no machine-to-machine contract. decision API needs single and bounded batch evaluation without extending browser sessions or CSRF exceptions to public runtime clients. Requests must remain tenant-safe, retry-safe, attributable, and reproducible against one immutable deployment even when the working model changes.

## Decision

- Runtime clients authenticate with service-scoped, 256-bit random bearer keys. RuleCraft returns the full key exactly once and stores only its prefix and SHA-256 digest. Keys are tenant/project/service qualified, owner-managed, capped at 20 active keys per service, revocable, and never written to audit metadata.
- `/api/runtime/v1/**` uses a dedicated stateless security chain. Browser sessions, cookies, and CSRF state do not authenticate this boundary. Missing, malformed, unknown, cross-service, and revoked credentials produce a uniform non-disclosing response.
- Every evaluation resolves one currently active runtime sandbox deployment and executes only its stored artifact. Responses identify the deployment, decision version, and artifact fingerprint.
- Clients supply a request UUID. The canonical request hash and complete successful response are stored under the credential/service/request identity. Exact retries return the stored response; reuse with another operation or body conflicts. First-use races isolate the evidence insert in an independent transaction and converge on the committed winner response.
- Batch requests contain 1–100 ordered items with unique 1–80 character correlation IDs. One pinned artifact evaluates the entire batch; individual validation/evaluation failures become bounded per-item errors and do not discard successful siblings.
- Session-authenticated project members may run the same bounded batch operation from the runtime workbench. Only owners and tenant administrators may create, list, or revoke service API keys.
- Request history and audit events retain identities, counts, pin metadata, and outcomes, but not keys, input payloads, or output payloads. Durable response JSON exists only to satisfy idempotency and is tenant/service scoped.

## Consequences

Integrations can safely retry after timeouts and can prove exactly which immutable artifact produced a result. Key compromise is contained to one service and can be revoked without changing user sessions. Batch partial failure is explicit and correlated rather than all-or-nothing.

The Decision API does not provision external endpoints, environment routing, asynchronous jobs, or a general traffic-policy system. Security and observability services provide bounded local controls; shared ingress limits, long-term monitoring, and external exposure remain deployment responsibilities.

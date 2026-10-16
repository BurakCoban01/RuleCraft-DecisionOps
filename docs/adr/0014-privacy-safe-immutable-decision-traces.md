# ADR 0014: Privacy-safe immutable decision traces

## Status

Accepted — 2026-08-29

## Context

runtime sandbox and decision API execute immutable deployed artifacts through the authenticated sandbox, service API, and bounded batch paths. Operators need durable evidence of which version ran, which decisions participated, and why an outcome was produced. Persisting complete request and intermediate payloads would create an unnecessary sensitive-data store, while mutable or credential-agnostic traces would weaken auditability and tenant isolation.

## Decision

- Every sandbox, single API, API batch, and workbench batch evaluation receives an immutable evaluation identifier and a trace pinned to the deployment, decision version, and artifact fingerprint used at execution time.
- A trace records its tenant, project, service, execution channel, authenticated subject, request/correlation identifiers, status, duration, ordered decision steps, implementation kind, hit policy, matched rule, and field names.
- Raw request inputs and complete outputs are not retained in the trace store. Canonical SHA-256 fingerprints prove input/output identity. Boolean intermediate results may be shown directly; all other intermediate values are replaced with their JSON type, fingerprint, and bounded shape metadata.
- Session users may inspect traces only through existing project visibility and service scope. A runtime API key may retrieve only a trace created by that same credential and service; all scope mismatches use a non-disclosing not-found response.
- Trace persistence uses an independent transaction so failed evaluations remain explainable. Deterministic API evaluation identifiers include the credential subject and canonical request hash, allowing exact retries to converge without colliding with a corrected failed attempt.
- Generic audit events retain trace identity and execution metadata but no payload values. Trace history is bounded to the newest 100 service records and detail steps are ordered and immutable.

## Consequences

Operators and machine clients can link a runtime result to the exact deployed artifact and decision path without turning RuleCraft into a raw-payload archive. Credential isolation prevents one integration from inspecting another integration's evidence even when both target the same service.

Failed evaluations record the bounded failure and zero completed steps when execution stops before a decision result is produced. explainability does not add configurable retention, legal hold, trace analytics, or distributed tracing; runtime analytics and operational security own analytics and operational hardening.

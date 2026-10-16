# ADR 0012: Immutable local runtime deployments

## Status

Accepted — 2026-08-29

## Context

approval and promotion identifies one governed immutable version, but promotion deliberately performs no runtime activation. runtime sandbox needs a restart-safe environment boundary that proves the promoted artifact itself can be validated and executed without reading mutable authoring tables. It must also preserve supersession, retirement, rollback, actor, and failure evidence without implying external deployment.

## Decision

- Each decision service has one lazily created `SANDBOX` runtime environment with a single optimistic active-deployment pointer. It is local platform state, not a cloud, cluster, endpoint, or customer environment.
- A deployment attempt binds the current tenant/project/service-qualified promotion, change request, immutable decision version, artifact JSON, and artifact fingerprint. Only project owners and tenant administrators may activate, retire, or roll back; visible project members may inspect and evaluate.
- Deployment follows `CREATED → VALIDATING → ACTIVE | FAILED`. Activating a newer artifact moves the former active deployment to `RETIRED`. Explicit retirement clears the active pointer. Rollback changes the current active deployment to `ROLLED_BACK` and reactivates a previously successful `RETIRED` artifact from the same environment.
- Deploy commands require the exact current promotion revision and decision-version ID, serialize on the service/environment boundary, reject archived services and invalid/unpromoted versions, and use a client request UUID for retry-safe creation.
- Runtime evaluation reads only the artifact JSON stored on the active deployment. It uses the captured contract, types, graph topology, rule order, hit policy, and RCEL source; it never resolves the mutable working graph or implementations. Inputs and outputs are checked against captured types with bounded depth, collection, string, and node budgets.
- Every state transition is append-only actor evidence and every activation, failure, retirement, rollback, and evaluation is audited. Failure details are bounded and user-safe.
- The runtime sandbox exposes authenticated project-scoped management and evaluation routes. Scoped machine endpoints and batch evaluation use the separate Decision API contract. External environment provisioning, rollout traffic routing, and hosted deployment are not implemented.

## Consequences

decision API can route decision and batch APIs to the active immutable sandbox artifact without changing deployment semantics. Authoring changes after activation cannot change runtime results until a new version is reviewed, promoted, and deployed. Rollback is explicit and inspectable rather than a pointer mutation with lost history.

The local sandbox is intentionally single-environment and synchronous. Multi-environment promotion, rollout strategies, health orchestration, and externally reachable runtime infrastructure remain outside runtime sandbox.

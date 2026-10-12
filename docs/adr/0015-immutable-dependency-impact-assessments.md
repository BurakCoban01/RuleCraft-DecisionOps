# ADR 0015: Immutable dependency impact assessments

## Status

Accepted — 2026-08-29

## Context

RuleCraft already retains typed contracts, graph implementations, test and regression evidence, immutable versions, approval state, promoted baselines, and runtime deployments. Reviewers need one service-scoped view that explains how those records relate before a governed baseline changes. A derived dashboard alone is insufficient audit evidence, while making impact findings an implicit promotion gate would alter the protected approval state machine.

## Decision

- Dependency impact is computed for one tenant-qualified decision service. It follows transitive custom-type references upstream and records graph, scenario, version, review, promotion, regression, and active-runtime dependencies downstream.
- Findings use `LOW`, `MEDIUM`, `HIGH`, and `BLOCKED` risk. Missing evidence and ordinary drift are advisory warnings; invalid models, unimplemented decisions, failed regression, and runtime/promotion mismatch prevent a `REVIEWABLE` result. dependency impact does not add a hidden approval or promotion transition.
- The live source identity is a canonical SHA-256 hash of the complete dependency evidence: working-decision identity and validation, graph validation, findings, and inventory. Assessment capture rejects an outdated identity so governance, regression, and runtime changes are stale-guarded as well as model edits.
- An assessment stores immutable JSON evidence and a separate SHA-256 snapshot fingerprint. Capture is idempotent by tenant, service, and client request ID; an exact retry returns the existing assessment and conflicting reuse is rejected.
- Project members may read the live map and immutable history. Tenant administrators and project owners, editors, and reviewers may capture assessments; viewers receive an explicit governance-permission problem. Every lookup remains tenant, project, and service scoped.
- Assessment history is bounded to the newest 100 records in the UI/API and to 1,000 retained records per service. Captures emit payload-free audit metadata.

## Consequences

Authors and reviewers can inspect and export the blast radius, preserve the evidence used during review, and later distinguish that snapshot from the current model. The existing approval workflow remains the sole authority for promotion.

dependency impact does not implement cross-service dependency discovery, automated promotion blocking, retention policy, legal hold, external graph storage, or portfolio analytics. Runtime analytics and broader governance automation remain later-phase work.

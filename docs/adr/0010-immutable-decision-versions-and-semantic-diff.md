# ADR 0010: Immutable decision versions and semantic diff

## Status

Accepted — 2026-08-29

## Context

The working service, type, graph, implementation, and scenario aggregates are independently versioned and mutable. Reviewers need a durable change point that cannot become detached from what an author actually saw, and they need a semantic comparison rather than a noisy relational or canvas-layout diff. Approval and promotion require stable evidence separate from mutable authoring state.

## Decision

- A captured version is a service-scoped immutable snapshot containing service contract metadata, the transitive referenced-type closure, semantic graph nodes and edges, decision-table or RCEL implementations, and persisted scenarios.
- The snapshot excludes presentation-only graph coordinates, generated persistence identifiers where identity is already represented by a stable key, audit noise, and test-run history.
- A canonical, key-ordered JSON representation is fingerprinted with SHA-256. The browser first reads the current fingerprint and sends it with service and graph revisions; capture under repeatable-read isolation rejects any stale aggregate, including an implementation or scenario change that does not increment the service or graph.
- A required client request UUID makes an exact retry idempotent. A second capture with the same semantic fingerprint is rejected, and history is bounded to 1,000 versions per service.
- Readiness is captured as `VALID` only when the graph, every decision implementation, and every persisted scenario is valid. Invalid drafts remain capturable and visibly marked so review evidence is not lost.
- Semantic diff groups stable-key changes into `CONTRACT`, `GRAPH`, `IMPLEMENTATION`, and `TEST`, with `BREAKING`, `BEHAVIORAL`, or `NON_BREAKING` impact. Service name/description changes are non-breaking metadata; contract removals/changes are breaking; graph and implementation changes are behavioral.
- Project-visible roles may list and compare versions. Only owners and editors may capture. All lookups remain tenant/project/service qualified, and each capture records actor, timestamp, source revisions, and an audit event.

## Consequences

approval and promotion can bind review workflow to immutable version IDs and fingerprints without copying mutable working data. Comparisons are deterministic, reload-safe, and useful to authors and reviewers while avoiding layout churn.

Snapshots are intentionally service-local and capped. version review does not execute historical snapshots, approve them, promote them, roll them back, or select runtime deployments; those responsibilities remain with approval and promotion–explainability.

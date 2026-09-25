# ADR 0009: Durable bounded regression matrices

## Status

Accepted — 2026-08-29

## Context

test scenarios can execute one scenario and preserve its immutable result. Reviewers also need an aggregate answer across a deliberate selection, without introducing a queue or claiming historical-model execution that the current mutable authoring model cannot provide. A request retry must not create duplicate evidence, and a process stop must not leave a run looking indefinitely active.

## Decision

Regression execution is a synchronous, bounded orchestrator over the single decision execution engine and test scenarios scenario runner:

- each request selects 1–100 unique, tenant-scoped persisted cases and supplies a service-scoped client request UUID;
- the UUID and ordered selection are unique and immutable, making an exact retry return the original run while conflicting reuse receives a stable error;
- a `RUNNING` aggregate commits before evaluation, each case result commits independently, and the final `COMPLETED` aggregate records pass/fail/error counts;
- startup recovery changes abandoned `RUNNING` aggregates to `INTERRUPTED`; already committed results remain inspectable and the original selection can be rerun under a new request ID;
- every case result snapshots its position, case identity/key/name/version, expected and actual values, graph/evaluation identity, ordered implementation-qualified decision path, and a stable sanitized error where execution could not complete;
- all project-visible roles may run and inspect matrices, matching test scenarios execution permissions. Tenant and project scope is derived from the authenticated principal, never the request body;
- service history is bounded to the latest 20 runs and CSV export is generated from the loaded immutable result.

## Consequences

The matrix remains truthful under partial failure and process restart without Redis, a broker, or a background-worker claim. `FAILED` means the engine completed but expectations differed; `ERROR` means that case could not execute; `INTERRUPTED` means the aggregate stopped before all selected cases committed.

regression matrices records exact versions used by each evaluation but does not execute an old graph snapshot or define semantic version diffs, approval baselines, scheduling, cancellation, or distributed parallelism. Those require separate compatibility contracts.

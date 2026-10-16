# ADR 0008: Versioned test cases and immutable completed runs

## Status

Accepted — 2026-08-29

## Context

Decision authors need durable examples that remain governed with their service contract and expose behavioral changes individually or through a regression matrix. Storing only ad hoc sandbox responses would lose expected outcomes and authorship; storing mutable “latest result” fields on a case would erase the version context needed for regression and review.

## Decision

Each decision service owns relational `decision_test_case` aggregates and immutable `decision_test_run` children:

- cases carry tenant/project/service ownership, a service-unique stable key, typed input and expected-output JSON snapshots, audit identity, timestamps, and an optimistic version;
- case payloads are validated against the current catalog contract on every write, list, and run, making schema drift visible without silently rewriting saved examples;
- authoring follows project edit permissions and the active-service boundary, while any project-visible role may execute/read cases;
- runs always invoke the single decision execution engine and persist only completed comparisons, including test/graph versions, expected/actual outputs, ordered version-qualified decisions, actor, time, and pass/fail status;
- pass/fail comparison is recursive and treats numeric JSON nodes by decimal value;
- histories are bounded to the latest 20 in the test scenarios API and cases are bounded to 500 per service.

Deleting a case cascades to its runs. Test runs are not edited or deleted independently.

## Consequences

Authors can recover cases after contract evolution and reviewers can distinguish expectation mismatch from invalid test data. Immutable results retain the historical context used by matrices and review. PostgreSQL remains the only durable runtime dependency; no worker, queue, cache, or object store is required.

Individual scenario runs do not persist engine exceptions as completed runs and do not provide scheduling or baseline pinning. Bounded bulk selection, checkpointing, interruption recovery, and idempotent matrix requests belong to the separate regression-matrix contract.

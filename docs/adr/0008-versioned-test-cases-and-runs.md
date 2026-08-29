# ADR 0008: Versioned test cases and immutable completed runs

## Status

Accepted — 2026-08-29

## Context

Decision authors need durable examples that remain governed with their service contract and can expose behavioral changes before regression matrices adds matrix execution. Storing only ad hoc sandbox responses would lose expected outcomes and authorship; storing mutable “latest result” fields on a case would erase the version context needed for later regression and review work.

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

Authors can recover cases after contract evolution and reviewers can distinguish expectation mismatch from invalid test data. Immutable versions make later matrix, diff, and approval features possible without inventing historical context. PostgreSQL remains the only added runtime resource; no worker, queue, cache, or object store is required.

test scenarios does not persist engine exceptions as completed runs and does not claim suite scheduling, baseline pinning, bulk simulation, retries, or idempotent execution. Those require explicit contracts in subsequent extensions.

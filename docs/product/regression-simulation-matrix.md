# Regression and simulation matrix

Open **Regression** from a saved decision graph, or move from **Test cases** to **Open regression matrix**. The reload-safe route is `/projects/{projectId}/services/{serviceId}/graph/regression`.

## Build and run a matrix

Filter the persisted scenario inventory, select individual cases or **Select valid**, and run up to 100 unique scenarios. Invalid cases may remain selected deliberately: the matrix records a per-case `ERROR` with an actionable contract problem instead of losing the rest of the run.

Execution status has precise meaning:

- `PASSED` — evaluation completed and every expected output matched;
- `FAILED` — evaluation completed but at least one expected output differed;
- `ERROR` — the selected case could not execute, for example after contract drift;
- `INTERRUPTED` — the process stopped before every selected case committed.

Submitting the same generated request ID and identical selection returns the original run, so network retries do not duplicate evidence. A request ID cannot be reused for a different selection.

## Inspect evidence

The scorecard separates passes, expectation failures, execution errors, and committed coverage. Filter the case rows by status and expand a result to compare expected and actual outputs or inspect its ordered decision path. Test, graph, table/expression, and evaluation versions are retained with the immutable result. Recent history retains the latest 20 matrices for the service and can be exported as CSV.

After startup, an abandoned run is marked `INTERRUPTED`. Its committed case evidence stays visible; **Rerun selection** creates a new idempotent request using the same case IDs.

## Access and boundaries

Owners, editors, reviewers, and viewers with project visibility can execute and inspect regression matrices. Cross-project and cross-tenant identifiers are not disclosed. regression matrices runs the current saved decision implementation; it does not claim historical graph replay, scheduled suites, semantic diffs, approval baselines, promotion gates, or deployment selection.

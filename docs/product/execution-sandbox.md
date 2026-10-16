# Decision execution sandbox

Open **Evaluate** from a saved decision graph. The deep link is `/projects/{projectId}/services/{serviceId}/graph/sandbox` and survives reload.

## Evaluation model

- Enter values against the persisted service contract. Built-in values use type-specific controls; object, enum, and list contracts accept bounded JSON shaped by the project type definition.
- RuleCraft validates required, unknown, and recursively typed inputs before execution.
- A structurally valid graph executes decision nodes in deterministic dependency order, with node key as the stable tie-breaker for simultaneously ready decisions.
- Every decision must have exactly one valid table or expression implementation.
- `FIRST` tables choose the first enabled matching rule. `UNIQUE` tables must match exactly one enabled rule. A missing match is an explicit runtime problem.
- RCEL-1 runs its owned parsed AST with decimal arithmetic, lazy boolean/conditional branches, object-field access, ISO date/date-time comparisons, and the documented pure-function allowlist.

The response identifies the graph version, each implementation version, any matched table-rule identity, the ordered decision values, and the service outputs. This makes an individual result reproducible against the saved authoring state without claiming durable trace storage.

## Access and safety

Any user who can view the project may run its sandbox; evaluation does not grant edit rights. Project and tenant visibility is enforced server-side, and successful runs write metadata-only audit events without copying supplied business inputs into the audit record.

Invalid graphs, invalid implementations, missing implementations, input contract or size-limit failures, no-match/unique violations, missing expression fields, and division by zero return stable actionable problem codes. The sandbox is read-only with respect to authored decision state and does not deploy or promote a service.

Working-copy evaluations are synchronous and ephemeral apart from their metadata audit event. Use scenarios and regression matrices for saved assurance, and the Runtime workspace for immutable-artifact activation, single/batch APIs, and durable redacted traces. The working-copy sandbox does not claim historical replay or background scheduling.

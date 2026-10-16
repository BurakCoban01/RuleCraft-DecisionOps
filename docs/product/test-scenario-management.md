# Test and scenario management

Open **Test cases** from a saved decision graph. The deep link is `/projects/{projectId}/services/{serviceId}/graph/tests` and survives reload.

## Authoring scenarios

Each scenario has a stable lower-camel test key, name, optional description, typed service inputs, and typed expected outputs. Built-in fields use type-specific controls; object, enum, and list fields accept JSON shaped by the project type definition. Required contract fields must be supplied, unknown fields are rejected, and each service is bounded to 500 cases.

Administrators, owners, and editors can create, edit, or remove cases for active services. Writes use optimistic versions and audited commands, so stale editors refresh instead of overwriting concurrent changes. Removing a case deliberately removes its attached run history. Reviewers and viewers receive read-only authoring controls but may run and inspect visible cases. Archived services keep their scenarios readable and runnable but immutable.

Validity is recomputed against the current service contract whenever cases are listed or run. A contract change can therefore mark a previously valid case invalid without destroying its saved values. The scenario remains editable so an authorized author can repair the affected fields.

## Running and comparing

Each run executes through the deterministic decision execution engine against the current saved graph. RuleCraft compares every expected output recursively, including numeric value equality, ordered lists, and exact object fields. A run is `PASSED` when all expectations match and `FAILED` when execution succeeds but a value differs.

Completed runs are immutable and record:

- test-case version;
- graph version and evaluation identity;
- expected and actual outputs;
- ordered decision results with implementation versions and matched rule identities;
- result status, actor, and time.

The scenario grid shows the latest result and supports name/key search, individual execution, editing, removal, and the latest 20 run details. Invalid cases expose their first actionable contract issue and cannot run until repaired.

## Boundaries

Individual scenario actions run one case at a time. The regression workbench adds bounded, idempotent bulk selection and durable matrix evidence. These runs evaluate the current saved model; scheduled suites, tags, and historical-model replay are not implemented. Version review, promotion, activation, and machine evaluation have separate governed workspaces and contracts.

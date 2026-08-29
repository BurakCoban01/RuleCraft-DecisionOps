# Version diff and change review

Open **Versions** from a saved decision graph. The reload-safe route is `/projects/{projectId}/services/{serviceId}/graph/versions`.

## Capture a change point

Owners and editors can capture the current saved decision with a required label and optional review note. Each immutable version includes the service contract, all referenced custom types, semantic graph, decision-table or expression implementations, and persisted scenarios. Canvas positions and run history are excluded because they do not change decision meaning.

The capture form is guarded by service revision, graph revision, and a fingerprint of the complete working decision. If any tab or collaborator changes the contract, implementation, or scenarios, reload the working copy before retrying. Repeating the same request is safe; capturing an unchanged decision is rejected. Archived services remain reviewable but cannot create new versions.

`VALID` means the graph is structurally valid, every decision has a valid implementation, and every saved scenario is valid. An invalid draft can still be captured as review evidence and is clearly marked `INVALID`; validity is not approval.

## Compare versions

Choose any two captured versions and select **Compare versions**. The scorecard separates:

- `BREAKING` contract removals or incompatible contract/type changes;
- `BEHAVIORAL` graph, rule-table, or RCEL expression changes;
- `NON-BREAKING` additions, metadata, and scenario-only review changes.

Filter by contract, graph, implementation, or test, then expand **Review values** to inspect canonical before/after evidence. History and comparisons persist across reloads. Reviewers and viewers have read-only access; cross-project and cross-tenant identifiers are not disclosed.

## Boundaries

A captured version is immutable review evidence, not a deployed artifact. version review does not provide approval decisions, promotion stages, historical execution, rollback, or runtime selection. Those workflows begin in approval and promotion.

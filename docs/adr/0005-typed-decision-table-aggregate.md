# ADR 0005: Typed decision-table aggregate

- Status: Accepted
- Date: 2026-08-29

## Context

decision graph decision nodes provide stable typed interfaces but no executable rule authoring model. Decision tables must attach to those identities without duplicating service contracts, permit incomplete drafting, make row order and hit policy unambiguous, and remain safe when a decision interface evolves.

## Decision

Each decision node may own at most one relational decision-table aggregate:

- A table references its project, graph, and decision node and carries an optimistic version, validation status, audit identity, and either `UNIQUE` or `FIRST` hit policy.
- Ordered rules contain one input entry per current decision input and one JSON result matching the decision output. decision tables input entries are intentionally limited to `ANY` and typed `EQUALS`; richer comparisons and the safe FEEL-like grammar belong to expression authoring.
- `UNIQUE` rejects enabled rules whose input domains overlap. `FIRST` permits overlap and treats row order as executable behavior.
- Typed JSON validation is recursive for built-in scalar types and project object, enum, and list definitions. Invalid cells and incomplete enabled rules remain persisted as an `INVALID` draft rather than being discarded.
- Graph saves reconcile existing node and port identities instead of replacing them, preserving table foreign keys. When new decision inputs appear, reads expose regenerated `ANY` cells plus `RC-TABLE-SCHEMA-DRIFT`; an author must review and save before the table becomes valid again.
- Administrators, owners, and editors mutate active services. Reviewers and viewers read. Archived services are read-only. Initialization and updates are audited, tenant/project scoped, and version guarded.
- A missing table is a normal `204 No Content` state and initialization is an explicit command.
- A decision may own a table or expression, never both. Either implementation can be removed only through an authorized, version-guarded, audited command before switching kinds.

## Consequences

Later execution can consume deterministic ordered rules without inferring column meaning. Authors can preserve invalid drafts and recover from input evolution. The supported semantics are deliberately smaller than DMN: decision tables does not claim full DMN/FEEL conformance, expression rules, execution, version history, or promotion.

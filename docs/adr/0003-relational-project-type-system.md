# ADR 0003 — Relational project type system and service contracts

- Status: Accepted
- Date: 2026-08-29
## Context

Decision graphs, tables, expressions, tests, and runtime evaluation need one shared type vocabulary. Opaque schema JSON would be easy to store but difficult to authorize, validate, reference, migrate, and inspect for dependency impact. Service contracts also need ordered, queryable inputs and outputs.

## Decision

- Five built-ins are fixed for the initial documented subset: `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, and `DATE_TIME`.
- Project-scoped reusable types are `OBJECT`, `ENUM`, and `LIST`.
- Object fields, enum values, list item references, and decision-service input/output fields are relational ordered rows with stable keys.
- Every type reference contains exactly one built-in kind or custom type UUID. Custom references must resolve inside the same authenticated tenant and project.
- Custom-type cycles are rejected. Mutation payloads are bounded to 100 ordered fields or enum values per aggregate.
- Decision services are project-scoped catalog entries with typed ordered inputs and outputs and an `ACTIVE`/`ARCHIVED` catalog lifecycle. At least one output is mandatory.
- Owners, editors, and workspace administrators mutate catalog data. Reviewers and viewers can read only. The server derives tenant and project access from the session and membership.
- Aggregates carry optimistic versions. Writes and lifecycle changes are audited. Referenced types cannot be deleted.

## Consequences

PostgreSQL can enforce referential integrity and subsequent extensions can query dependencies without parsing documents. Aggregate replacement keeps field ordering deterministic. Type/version history, semantic diff, graph nodes, runtime validation, and dependency-impact visualization are intentionally layered in decision graph/version review/dependency impact rather than being simulated in typed catalog.

# ADR 0007: Deterministic in-process decision execution

## Status

Accepted — 2026-08-29

## Context

The persisted service contract, typed DAG, decision tables, and RCEL-1 expressions need one execution model before scenarios, deployment, batch evaluation, and durable traces can be layered safely. Execution must not infer types, reorder authored rules, call a host scripting engine, or weaken project authorization.

## Decision

RuleCraft executes the saved graph synchronously inside the Java modular monolith:

- service inputs are recursively validated against the catalog type system;
- only structurally valid acyclic graphs are executable;
- dependency-ready decision nodes are selected by stable node-key order;
- each node must resolve to exactly one currently valid table or expression;
- table rules preserve persisted position and implement only exact typed `EQUALS`/`ANY` with `UNIQUE` and `FIRST`;
- expressions reuse the RCEL-1 parser and type checker, then evaluate the owned sealed AST with decimal arithmetic and a closed pure-function set;
- results contain graph and implementation versions, ordered decision values, matched rule identities, and service outputs;
- project visibility is enforced before evaluation, while audit records contain execution metadata but not raw business inputs.

The decision execution sandbox stores no evaluation aggregate. Each call receives a new response identity and re-evaluates the saved state. It is not a deployment, public runtime API, retry protocol, or durable explain trace.

## Consequences

Identical saved versions and inputs have deterministic semantics without an external engine or host-runtime escape surface. Scenario and trace services persist selected evidence around this core. Synchronous execution remains bounded by graph, rule, source, token, and AST-depth limits. Durable request history, idempotency, captured-artifact selection, and machine authentication have explicit runtime/API contracts; background batch scheduling is not implemented.

No new infrastructure or schema is required for decision execution. The engine reuses PostgreSQL-backed authored state and the existing audit boundary, avoiding additional memory, disk, broker, or container cost on the constrained development host.

# ADR 0004: Typed decision graph aggregate

- Status: Accepted
- Date: 2026-08-29

## Context

Decision-service contracts from typed catalog need a persisted authoring graph that later decision tables, expressions, execution, versioning, and governance can reference. The graph must preserve project type identity, prevent ambiguous or cyclic dependencies, tolerate incomplete drafting, and remain recoverable when the service contract changes.

## Decision

Each decision service owns at most one `decision_graph` aggregate. Its relational children are nodes, typed ports, and directed edges:

- `INPUT` and `OUTPUT` nodes mirror service fields. Their key, label, type, and required flag are contract-controlled.
- `DECISION` nodes have author-controlled typed input ports and exactly one typed output port. decision tables and expression authoring will attach implementations to these stable node identities.
- Edges always connect an output port to one input port. The source and target type references must match exactly; an input accepts one incoming edge.
- Structural corruption, foreign type references, duplicate identities, self-links, and cycles are rejected. Missing required connections and orphan decisions are saveable validation issues, leaving the aggregate `INVALID`.
- Aggregate replacement is transactional and guarded by the graph version. Every mutation is authorized at the project boundary and audited.
- A service-contract change produces an explicit drift state. Contract synchronization regenerates controlled nodes, preserves decision nodes and decision-to-decision edges, and removes connections that touched the old contract.
- A missing graph is a normal `204 No Content` read state; initialization is an explicit audited command.

## Consequences

The runtime can traverse a deterministic typed DAG without inferring port meaning. Draft authoring remains possible before a graph is executable. Service-schema evolution is visible and recoverable, but synchronization deliberately requires reconnection of affected contract edges. Full decision semantics, table hit policies, expressions, execution, and version history remain later-phase concerns.

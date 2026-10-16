# Decision engine

RuleCraft evaluates a documented subset of typed business decisions. It is inspired by DMN/FEEL and does not implement or claim full conformance to either standard.

## Types and contracts

Built-in values are `TEXT`, `NUMBER`, `BOOLEAN`, `DATE`, and `DATE_TIME`. Custom types are project-scoped objects, enums, or lists. Objects have ordered named fields with required flags; lists reference a built-in or custom item type. Enum values use stable stored keys. Type references must belong to the same project, and recursive type cycles are rejected.

A decision service declares typed inputs and outputs. Runtime validation rejects unknown service fields, missing required inputs, incompatible JSON values, malformed dates, enum mismatches, and unexpected object fields. Decimal numbers use `BigDecimal` semantics rather than binary floating-point arithmetic.

## Graph model

The persisted graph has input, decision, and output nodes. Service contract nodes are controlled by the service schema. Decision nodes expose typed ports and have one result. Edges connect output to input ports of exactly matching types; duplicate incoming connections and cycles are invalid.

Incomplete drafts can be stored and inspected. Validation reports missing required connections, contract drift, invalid node/port shapes, and implementation problems. Saving authoring data requires the expected aggregate revision. A stale editor receives a conflict and must refresh.

At execution, service inputs populate the graph's input ports. The engine repeatedly selects the first ready decision in stable node-key order. Resolved optional inputs remain distinct from inputs that have not yet been evaluated. Service outputs are collected only after their dependencies are resolved.

## Tables

A decision node can have either a table or an expression, never both. A table contains at most 500 ordered rules. Each rule has an enable flag, one entry per decision input, and a typed JSON result.

| Entry / policy | Meaning |
|---|---|
| `ANY` | This input does not constrain the rule |
| `EQUALS` | The input must equal the stored typed JSON value |
| `UNIQUE` | Exactly one enabled rule must match; overlapping authoring rules are diagnosed |
| `FIRST` | Select the first enabled matching rule in stored order |

No match returns a stable evaluation problem. A `UNIQUE` multiple match is also an error; the engine never silently picks a winner. Unsupported operators and hit policies are rejected.

## Expressions

RCEL-1 has a lexer, parser, typed AST, type checker, and evaluator owned by the application. Expressions may read their declared inputs and object fields, apply typed operators, use a conditional, and call an explicit allowlist of pure functions. Boolean operators and conditional branches short-circuit.

The runtime cannot call host languages, reflection, methods, I/O, network, environment variables, or arbitrary functions. See [the language reference](expression-language.md) for precedence, functions, diagnostics, and resource limits.

## Working-copy and captured execution

The authoring sandbox evaluates the saved current graph and implementations. It is useful for iteration but is not the active release.

The runtime sandbox and Decision API evaluate a captured immutable artifact. That artifact contains the contract, referenced types, graph, and implementations selected through version capture, approval, promotion, and activation. Working-copy changes cannot change the active artifact. Responses identify the deployment, version, and artifact fingerprint.

Rollback changes the active pointer to an eligible prior artifact and retains the transition history. It does not edit old versions, erase traces, or revert the mutable authoring model.

## Assurance and failures

Scenarios persist inputs and expected outputs. A scenario run stores an immutable expected/actual comparison with source revisions. Regression matrices execute a bounded deliberate selection, checkpoint each result, and report pass, fail, error, or interrupted state.

Runtime failures use stable `RC-*` problem codes for invalid graph/input/implementation, missing fields, no table match, division by zero, stale artifacts, and retry conflicts. Batches preserve ordering and item correlation, and distinguish complete success, complete failure, and partial results.

Trace records show ordered decision steps and matched-rule metadata. They retain payload fingerprints and field names rather than raw runtime inputs, and redact non-boolean intermediate results. Test results and idempotent API response storage have different retention semantics; see [security](../SECURITY.md).

## Deliberate limits

There is one local runtime environment per service, bounded synchronous batch/regression execution, and no scheduler or distributed worker fleet. Microbenchmarks measure sequential engine work only. These constraints make correctness and failure behavior inspectable; they are not evidence of production capacity.

# ADR 0006: Safe RCEL-1 expression subset

- Status: Accepted
- Date: 2026-08-29

## Context

Typed decision nodes need an expression implementation that can be validated now and executed deterministically by decision execution. Embedding Java, JavaScript, SpEL, a general scripting engine, or an unrestricted DMN/FEEL interpreter would create ambiguous semantics and an unacceptable host-runtime escape surface.

## Decision

RuleCraft defines `RCEL-1`, a documented DMN/FEEL-inspired subset parsed and type-checked by owned Java code. expression authoring validates and persists expressions but does not execute them.

The grammar supports:

- boolean, decimal-number, and double-quoted text literals;
- decision input references and project-object field paths such as `applicant.age`;
- `not`, unary `-`, `*`, `/`, `+`, `-`, comparisons, equality, `and`, and `or` with fixed precedence;
- `if condition then value else fallback`;
- allowlisted pure functions: `lower`, `upper`, `contains`, `length`, `date`, and `dateTime`.

Typing is exact and deterministic. Arithmetic is numeric; boolean operators require booleans; ordered comparisons support numbers, dates, and offset date-times; conditional branches must share a type; the final type must match the decision output. Enum comparison/result literals must be valid stored keys. Object fields resolve only through persisted project type definitions.

The parser enforces 12,000 source characters, 2,048 tokens, and 64 expression nesting levels. Unknown characters, names, fields, functions, arity, operators, escapes, and type combinations produce stable line/column diagnostics. There is no reflection, method dispatch, host object access, loops, assignment, mutation, I/O, network, process, environment, filesystem, dynamic loading, or general function registration.

Each decision node may have a table or expression, never both. Both implementation kinds have explicit version-guarded, audited removal commands so an authorized author can deliberately switch. Expression validity is recomputed against the current graph interface on reads and summaries.

## Consequences

decision execution can evaluate a small, frozen AST with bounded semantics instead of interpreting arbitrary code. Invalid drafts remain persistable and actionable. RCEL-1 is intentionally not full FEEL or DMN conformance; grammar changes require a new language version and compatibility decision.

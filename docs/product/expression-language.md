# RCEL-1 expression authoring

Choose **Expression** on a decision node without an implementation. The deep link is `/projects/{projectId}/services/{serviceId}/graph/nodes/{nodeId}/expression` and survives reload.

## Language reference

RCEL-1 expressions return one value matching the decision node's result type.

```text
if score >= 700 and lower(country) = "us" then true else false
```

- Literals: `true`, `false`, decimal numbers, and double-quoted text.
- References: input port keys such as `score`; object fields such as `applicant.age`.
- Arithmetic: unary `-`, `*`, `/`, `+`, `-` on numbers.
- Comparison: `<`, `<=`, `>`, `>=` on numbers, dates, or date-times.
- Equality: `=` and `!=` on compatible values. Enum literals use their quoted stored key.
- Logic: `not`, `and`, `or` on booleans.
- Conditional: `if condition then value else fallback`; both result branches must have the same type.
- Pure functions: `lower(text)`, `upper(text)`, `contains(text, fragment)`, `length(textOrList)`, `date("2026-08-29")`, and `dateTime("2026-08-29T10:30:00+03:00")`.

Precedence from strongest to weakest is parentheses, unary, multiplication/division, addition/subtraction, ordered comparison, equality, `and`, then `or`. Use parentheses when business intent would otherwise be difficult to scan.

## Validation and recovery

Save incomplete or invalid drafts and use the line/column diagnostics to resolve unknown inputs/fields/functions, malformed syntax, wrong function arity, incompatible operators, branch mismatch, or result mismatch. The typed-context panel lists the current inputs and expected result.

If the graph interface changes, validity is recomputed against the current port keys and types. Stale saves or removals require a reload. Owners, editors, and workspace administrators may author active services; reviewers and viewers receive a read-only expression. Archived services remain readable but immutable.

A decision can have either a table or expression. Use the two-step removal action to deliberately clear the current implementation before choosing the other kind.

## Security boundary

RCEL-1 has no host-language calls, methods, assignment, loops, reflection, filesystem, environment, process, network, I/O, or side effects. Unknown functions are rejected. Source, token, and nesting bounds protect validation resources. expression authoring validates only; deterministic evaluation and runtime error behavior are introduced in decision execution.

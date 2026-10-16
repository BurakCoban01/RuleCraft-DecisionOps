# RCEL-1 expression language

RCEL-1 is RuleCraft's bounded, typed expression subset. It is inspired by DMN/FEEL but is not a full FEEL implementation and does not require an external decision engine.

```text
if score >= 700 and lower(country) = "us" then true else false
```

An expression returns one value matching its decision node's output type. The current input ports form its readable symbol context. Unknown names, fields, functions, and incompatible results produce line/column diagnostics.

## Values and references

- Boolean literals: `true`, `false`.
- Decimal numeric literals and unary `-`.
- Double-quoted text with supported quote, backslash, newline, carriage-return, and tab escapes.
- Input keys such as `score`, and object fields such as `applicant.age`.
- Enum literals use their stored quoted value key where the expected custom type permits them.
- `date("2026-08-29")` and `dateTime("2026-08-29T10:30:00+03:00")` create the supported temporal values.

There is no assignment, loop, collection comprehension, mutation, or arbitrary method call.

## Operators

| Operator | Operand contract |
|---|---|
| Unary `-`, `+`, `-`, `*`, `/` | Numeric operands; division by zero is a stable runtime problem |
| `<`, `<=`, `>`, `>=` | Compatible number, date, or date-time values |
| `=`, `!=` | Compatible typed values |
| `not`, `and`, `or` | Boolean operands |
| `if condition then value else fallback` | Boolean condition and compatible result branches |

Precedence, strongest first: parentheses, unary, multiplication/division, addition/subtraction, ordered comparison, equality, `and`, then `or`. Use parentheses to make business intent explicit. Boolean operators and conditional branches short-circuit.

Arithmetic uses Java decimal semantics; division uses the evaluator's bounded decimal precision. Temporal values retain their ISO-8601 representation. This reference describes the supported subset rather than implying unspecified FEEL coercion rules.

## Pure functions

| Function | Contract |
|---|---|
| `lower(text)` / `upper(text)` | Case conversion |
| `contains(text, fragment)` | Text containment |
| `length(textOrList)` | Length of text or a typed list |
| `date("YYYY-MM-DD")` | ISO-8601 date literal |
| `dateTime("...offset...")` | ISO-8601 offset date-time literal |

The function list is closed. RCEL-1 cannot invoke Java, JavaScript, reflection, the filesystem, environment variables, a process, network, I/O, or a general function registry.

## Resource and failure boundaries

Source is limited to 12,000 characters, tokenization to 2,048 tokens, and expression/AST nesting to 64 levels. Unary chains and left-deep binary trees use the same depth budget as parenthesized expressions. Captured syntax validation and evaluation enforce the same parser boundary before recursive type checking or execution.

Malformed syntax, wrong arity, unknown functions, unsafe names, incompatible types, missing runtime fields, division by zero, and exceeded budgets produce stable diagnostics. A valid syntax tree is not permission to read values outside the declared input context.

## Authoring and execution

Authors may save invalid drafts and use the expression workbench's diagnostics to correct them. Graph-interface changes revalidate expressions; optimistic revisions reject stale writes. Archived services and read-only project roles cannot mutate expressions.

A decision node has one table or one expression. Switching implementation requires explicitly removing the existing one. Working-copy evaluation uses the saved authoring model; activated execution uses the captured immutable artifact. See [the decision engine](decision-engine.md), [API](api.md), and [security](../SECURITY.md).

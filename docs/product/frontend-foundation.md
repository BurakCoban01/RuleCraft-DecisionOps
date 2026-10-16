# Workbench interface

RuleCraft is a decision-authoring workbench for authors, reviewers, operators, and administrators. Its navigation follows persisted business objects and the decision lifecycle.

## Navigation and roles

Projects establish the working scope. A service opens its typed contract and graph; tables, expressions, evaluation, test cases, regression, versions, runtime, impact, and analytics stay attached to that service. Workspace administration and Operations are visible only to administrators. Client visibility supplements server authorization and never grants permission by itself.

## Working surfaces

- Graphs keep typed input, decision, and output nodes visible with validation and connection controls.
- Tables expose ordered rules, typed JSON values, enabled state, and explicit hit-policy badges.
- Expressions pair source with typed context and line/column diagnostics.
- Scenario and regression workbenches separate expectations, execution failures, and immutable results.
- Version review, promotion, and runtime history distinguish mutable drafts from governed and activated artifacts.
- Traces, dependency impact, analytics, and Operations expose evidence from persisted records with role-appropriate filters, actions, history, and exports.

## States and input

Loading, first-use empty, validation, stale revision, unavailable service, permission, archived/read-only, and success states share the product vocabulary and preserve an actionable next step. Browser history and direct object routes preserve context across reloads.

The interface uses semantic controls, visible keyboard focus, non-drag connection/reorder alternatives, text labels alongside semantic colors, system fonts, and light/dark themes. At compact widths the navigation becomes a labeled header and work surfaces retain their own scroll and action hierarchy.

Browser acceptance covers 1440×900 and 360×740 layouts, persisted journeys, console/network failures, and page overflow. This automated evidence and visual review do not substitute for a representative-user or real screen-reader study; see [testing](../testing.md).

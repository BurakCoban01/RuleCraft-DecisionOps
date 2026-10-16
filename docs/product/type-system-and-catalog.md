# Type system and decision-service catalog

Inside a project, RuleCraft separates reusable business types from decision-service contracts.

## Reusable types

- Object types define ordered named fields and requiredness.
- Enum types define ordered stored values and human-readable labels.
- List types name one built-in or custom item type.
- Custom references remain inside the project and cannot form cycles.

Editors can create, revise, and delete unused types. A type used by another type or service contract is protected with an actionable conflict rather than silently breaking dependents.

## Decision services

A decision service is the catalog identity for a business decision. Its ordered input and output fields reference built-in or reusable types. Services can be edited, archived, restored, filtered, and opened through a stable project URL. Archiving preserves the contract for later governance work.

The workbench exposes loading, empty, authoring, validation, conflict, stale, permission, archived, error, and compact responsive states. The graph modeler attaches typed nodes and connections to these persisted service contracts; downstream authoring and runtime checks use the same type identities.

# Decision-table authoring

Open a persisted graph and choose **Table** on a decision node. The deep link is `/projects/{projectId}/services/{serviceId}/graph/nodes/{nodeId}/table` and survives reload.

## Authoring model

- Initialize the table from the decision node's current typed inputs and result.
- Add, remove, enable, disable, and reorder rules in the typed grid.
- Use `ANY` when an input does not constrain a rule or `EQUALS` with a JSON value matching the column type.
- Enter the result as typed JSON. Text, dates, date-times, and enum values are JSON strings; numbers and booleans are unquoted.
- Choose `UNIQUE` when no two enabled rules may match the same inputs. Choose `FIRST` when ordered fallback behavior is intended.
- Save invalid drafts and use the validation panel and highlighted cells to resolve type, completeness, overlap, and schema-drift issues.

Owners, editors, and workspace administrators can author active services. Reviewers and viewers receive the persisted table read-only. Archived services remain readable but cannot be changed.

A decision can have either a table or expression. Use the two-step removal action to deliberately clear the table before choosing an expression implementation.

## Safety and recovery

Table saves are version guarded. A stale editor must reload instead of overwriting another author. The API rejects foreign project/node access, non-decision attachments, duplicate rule or cell identities, malformed rule shapes, unsupported operators, oversized aggregates, and more than 500 rules.

If the decision gains an input, existing rows receive a recoverable `ANY` cell and the table reports schema drift until an author reviews and saves it. Navigation warns before discarding unsaved table changes. Loading, missing, invalid, stale, unavailable, permission, archived, success, and read-only states are explicit.

Tables support `ANY` and typed `EQUALS` with `UNIQUE` and `FIRST`. Numeric equality ignores representation differences such as `700` versus `700.0`, including overlap validation. Use an RCEL-1 expression implementation for ordered comparisons and conditional logic; scenarios, semantic review, and promotion govern the surrounding service lifecycle.

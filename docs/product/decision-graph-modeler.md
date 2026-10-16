# Decision graph modeler

## Viewport and typed connections

The desktop canvas fits its persisted nodes to the available workbench space. Narrow canvases start at readable actual size with owned two-dimensional scrolling; **Fit to content** is an explicit overview choice. **Actual size** and zoom controls also change only the viewport, not the saved model. The canvas can receive keyboard focus. **Inspect connections** opens the validation and wiring inspector; hiding it gives the model the full workbench width.

Input and result ports occupy separate sides of each decision card. Connections attach to measured port anchors, including wrapped labels, rather than fixed row estimates. Forward dependencies detour around intervening cards. Drag movement accounts for the current zoom; each card's **Actions** disclosure retains non-drag move buttons and interface/removal actions.

The decision graph is opened from a decision service at `/projects/{projectId}/services/{serviceId}/graph`. It is a persisted, project-authorized workbench rather than a diagram-only view.

## Authoring model

- Initialize a graph from the service's typed input and output contract.
- Add decision nodes with ordered typed input ports and one typed result.
- Connect only type-compatible output and input ports. Each input accepts one source.
- Position decisions by pointer drag or keyboard-accessible nudge controls.
- Save incomplete work and use the validation inspector to resolve required connections, missing decisions, and orphan nodes.
- Edit or remove decision nodes; connections touching removed or type-changed ports are removed locally before save.
- Synchronize contract nodes after a service-schema change. Decision nodes and their internal links remain; affected boundary connections must be re-established.

Owners, editors, and workspace administrators can author active services. Reviewers and viewers receive the same persisted graph as a read-only view. Archived services are readable but cannot be changed.

## Safety and recovery

Graph saves are version guarded. A stale editor receives a refresh action instead of overwriting another author's work. The API rejects foreign project types, contract-node tampering, cycles, type mismatches, duplicate keys or ids, multiple incoming links, oversized aggregates, and coordinates outside the bounded workspace. Empty, loading, unavailable, invalid, stale, permission, archived, success, and contract-drift states are explicit.

Decision tables and expressions provide mutually exclusive implementations for decision nodes. A saved graph can be opened in the execution sandbox for an ad hoc request or in **Test cases** for persisted typed examples and immutable pass/fail history. The Regression, Versions, Runtime, Impact, and Analytics workspaces connect assurance, governance, activation, and operational evidence to the same service.

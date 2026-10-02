# Runtime deployment sandbox

Open a decision graph and choose **Runtime**. This workspace activates the governed immutable version in RuleCraft’s local sandbox; it never publishes an external endpoint.

## Activate a promoted version

The release bar shows the current promoted baseline beside the active artifact. Owners can choose **Deploy promoted version** when those versions differ. RuleCraft records a deployment attempt, validates the captured artifact, and either activates it or retains a bounded failure code and explanation.

Activation is retry-safe. A stale promotion or environment tab asks you to refresh rather than overwriting newer state. Archived services and services without a valid promoted baseline cannot activate.

## Inspect and operate history

Deployment history shows its sequence, captured version, state, fingerprint, actor, and timestamp. Select an attempt to inspect the append-only state timeline.

- `ACTIVE` is the single artifact used by sandbox probes.
- `RETIRED` is a previously successful artifact that can be selected for rollback while another deployment is active.
- `ROLLED BACK` identifies the deployment replaced by an explicit rollback.
- `FAILED` preserves validation evidence and never becomes active.

Owners may retire the active artifact, which makes evaluation unavailable, or roll back from the current active artifact to an earlier retired artifact. Viewers, editors, and reviewers can inspect runtime evidence and evaluate the active artifact but cannot change activation state.

## Probe the active artifact

The **Active artifact probe** builds inputs from the service contract and executes the stored immutable artifact. Results show the artifact fingerprint, service outputs, ordered decision path, implementation kind, and matched rule where applicable.

Working-copy edits do not affect this result. A behavior change reaches the runtime sandbox only after capture, review, approval, promotion, and a new deployment.

## Boundary

runtime sandbox is an authenticated, project-scoped local runtime environment. It does not create a public decision API, batch endpoint, external deployment, traffic route, secret, or infrastructure resource. Public API and batch evaluation begin in decision API.

# Approval and promotion workflow

Open **Versions** from the decision graph. The **Approval & promotion** workspace appears below immutable history and semantic comparison.

## Open and submit a review package

Owners and editors can open one change request for the latest valid captured version. Add a concise title and evidence summary, then submit the draft. The candidate cannot be edited inside the request: requested fixes require a new saved model, a new immutable version, and a new request.

If a newer version is captured, the older request is marked `STALE`. It remains readable but can no longer be approved or promoted. Invalid versions and archived services cannot enter or advance through the workflow.

## Review and promote

Owners and reviewers can approve, request changes, or reject a submitted request. Change requests and rejections require a decision note. Only an owner can select **Promote baseline** after approval.

The four-stage indicator separates draft, review, approved, and promoted states. Every action records the actor, timestamp, transition, and decision note in an immutable timeline. Repeated network submissions are safe and stale tabs receive an actionable refresh conflict.

The current governed baseline banner identifies the promoted immutable version and promoter. Promotion is a governance decision only; it does not deploy or activate a runtime environment.

## Access and boundaries

- `OWNER` — author, submit, review, and promote;
- `EDITOR` — author and submit;
- `REVIEWER` — inspect and decide reviews;
- `VIEWER` — inspect only.

Cross-project and cross-tenant identifiers are not disclosed. approval and promotion does not provide runtime deployment, environment activation, rollback, or historical execution; those begin in runtime sandbox and subsequent extensions.

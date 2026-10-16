# Decision API and batch evaluation

Open a decision graph and choose **Runtime**. After an owner activates the governed baseline, the **Decision API** section provides service-scoped credentials, a batch console, and recent idempotency evidence.

## Create and protect a service key

Owners and tenant administrators can label and create up to 20 active keys for a decision service. Copy the secret immediately: RuleCraft shows it once and retains only a recognizable prefix and a one-way digest. If a client or key is no longer trusted, choose **Revoke**; subsequent calls receive the same non-disclosing unauthorized response as an unknown key.

Keys authorize only the service that created them. They do not grant browser access, project navigation, or access to another tenant or service.

## Evaluate one decision

Send a JSON request to:

```text
POST /api/runtime/v1/decisions/{serviceKey}/evaluate
Authorization: Bearer {service API key}
```

The body contains a client-generated `requestId` UUID and contract-shaped `inputs`. The response includes outputs plus the evaluation ID, deployment ID, decision-version ID, and full artifact fingerprint.

Retry the identical request with the same ID to receive the exact stored response. Reusing that ID with different content or for a batch returns a conflict. A working-copy edit has no effect until it is captured, approved, promoted, and deployed.

## Run a bounded batch

The machine endpoint and workbench accept 1–100 ordered items. Each item needs a unique correlation ID and its own inputs. RuleCraft pins the full request to one active artifact and returns each item in input order.

- `SUCCEEDED` means every item evaluated.
- `PARTIAL` means successful outputs and bounded item errors are both present.
- `FAILED` means every item failed; the response still preserves each correlation ID and actionable error code.

The workbench sample can be edited as JSON and is useful for integration smoke tests. Recent requests show operation, request ID, item count, fingerprint, and time. They deliberately omit inputs, outputs, and secrets.

## Operational boundary

The Decision API provides synchronous single and batch evaluation against the active immutable sandbox artifact. Trace retrieval and operational analytics use persisted evidence; process-local rate controls are bounded. External exposure, shared ingress limits, environment routing, asynchronous jobs, and external monitoring are deployment responsibilities, not effects of creating an API credential.

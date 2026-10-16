# API contracts

RuleCraft exposes separate browser-authoring and machine-evaluation boundaries. OpenAPI is available in local development at `http://127.0.0.1:50110/v3/api-docs`. The production profile disables it and nginx returns 404 for external OpenAPI and actuator paths.

## Browser API

`/api/v1/session` provides anonymous session/CSRF bootstrap, sign-in, password change, and sign-out. Browser writes require the CSRF token returned by the application. Server-side session state establishes tenant identity.

Most decision-authoring resources are rooted at:

```text
/api/v1/projects/{projectId}/decision-services/{serviceId}
```

| Resource suffix | Responsibility |
|---|---|
| Project `/types` and `/decision-services` | Reusable types and service contracts |
| `/graph` | Typed graph and contract synchronization |
| `/graph/nodes/{nodeId}/decision-table` | Table authoring |
| `/graph/nodes/{nodeId}/decision-expression` | RCEL-1 authoring |
| `/sandbox/evaluations` | Saved working-copy evaluation |
| `/tests`, `/tests/{testId}/runs` | Scenarios and immutable comparisons |
| `/regression-matrices` | Bounded regression execution and result history |
| `/versions`, `/versions/working`, `/versions/diff` | Immutable capture and semantic comparison |
| `/change-requests` | Submit, review, approve, reject, request changes, and promote |
| `/runtime-sandbox` | Activation, retirement, rollback, and active-artifact evaluation |
| `/api-credentials` | Owner-managed create, inspect, revoke |
| `/decision-api/requests`, `/decision-api/batches` | Request history and workbench batches |
| `/decision-traces` | Scoped trace search and detail |
| `/dependency-impact` | Live dependency map and immutable assessments |
| `/runtime-analytics` | Bounded trace metrics and artifact benchmarks |

`/api/v1/operations` and `/api/v1/operations/recoveries` require a tenant administrator. Account administration is under `/api/v1/users`; project membership is under `/api/v1/projects/{projectId}/members`.

Consult generated OpenAPI or the controller record types for complete command/response fields. Revisions, source fingerprints, and client request IDs are part of the contract, not optional client decoration.

## Machine evaluation API

Create a service-scoped key in the Decision API console after activating a promoted version. Save the once-visible value in the calling system's secret store. Calls use the `Authorization: Bearer` header and do not create browser sessions.

Single evaluation:

```sh
curl -X POST http://127.0.0.1:50113/api/runtime/v1/decisions/ELIGIBILITY/evaluate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $RULECRAFT_API_KEY" \
  -d '{"requestId":"ca7ea338-72ba-4d20-a4dc-fd0312ed0c72","inputs":{"score":710}}'
```

The service key and input names must match the configured artifact. The response includes `requestId`, `evaluationId`, `serviceKey`, `deploymentId`, `decisionVersionId`, `artifactFingerprint`, and typed `outputs`.

Batch evaluation:

```json
{
  "requestId": "ad5bda01-4046-4cca-a36f-f9fddfca771a",
  "items": [
    { "correlationId": "case-001", "inputs": { "score": 710 } },
    { "correlationId": "case-002", "inputs": { "score": 620 } }
  ]
}
```

Send this body to `POST /api/runtime/v1/decisions/{serviceKey}/batch`. A batch contains 1–100 items and each needs a unique correlation ID of 1–80 characters. It pins one active artifact for all items. Responses retain item order and report `SUCCEEDED`, `FAILED`, or `PARTIAL` with individual output or stable error fields.

`GET /api/runtime/v1/decisions/{serviceKey}/traces/{evaluationId}` retrieves a trace only when the credential owns that evaluation. Workbench/session trace visibility does not expand a machine key's scope.

## Retry semantics

Every evaluation command requires a UUID `requestId`. Canonical JSON hashing and persisted response evidence make an exact successful retry return the original result, even after a later deployment becomes active. Reuse with different operation/content returns a conflict. Generate a new ID for a changed request.

Batch requests record ordered partial results. A failed single evaluation returns a problem and can be retried after correcting its cause; do not assume every failed request creates a completed idempotent response record.

## Errors and controls

Errors use `application/problem+json` with a stable `code` and actionable `detail`; callers should branch on `code`, not parse prose. Common classes are authentication/authorization (`401` / `403`), scoped missing objects (`404`), stale/version/request conflicts (`409`), validation failures (`400`), and bounded rate denial (`429`).

Runtime keys have a process-local request window; a denied response includes retry metadata. A shared ingress limiter is required across replicas. The supplied edge caps bodies at 1 MB and does not publish PostgreSQL, API, or management ports directly.

See [security](../SECURITY.md) for credential handling, data retention, and deployment requirements.

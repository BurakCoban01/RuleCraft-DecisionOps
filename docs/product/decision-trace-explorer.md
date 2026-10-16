# Decision trace explorer

Trace detail keeps request correlation, artifact identity and the ordered path visible together. Non-boolean steps show their redacted type and available length/size metadata; expand **Fingerprint** for the full identity. **Artifact and payload identities** exposes full deployment, artifact and payload fingerprints. These disclosures change presentation only: raw request inputs and redacted values are not revealed or reconstructed.

RuleCraft records privacy-safe evidence for evaluations made through the local runtime sandbox, the single decision API, and both API and workbench batches. Open a decision service, choose **Runtime**, and use the **Decision trace explorer** below the integration console.

## Find an evaluation

Filter by channel or outcome, or search for an evaluation ID, request ID, correlation ID, subject, or artifact fingerprint. The history is newest first and returns at most 100 records. Selecting an item opens immutable detail; filters have distinct empty and no-match states and can be cleared without leaving the runtime workspace.

## Read the evidence

The detail view identifies the deployment, decision version, artifact fingerprint, authenticated subject, duration, and outcome. Its ordered path lists each completed decision, the implementation kind, required input field names, table hit policy and matched rule when applicable, and a privacy-transformed result. Exporting trace JSON downloads the same scoped evidence currently shown.

Raw request inputs are never stored in decision traces. Input and output fingerprints allow identity comparison without retaining values. Boolean intermediate results are visible because they are the primary decision outcome; strings, numbers, objects, and arrays are replaced by type, fingerprint, and bounded length or size metadata. A failed evaluation shows its stable error code and safe detail, and may have no completed steps.

## Access boundary

Project viewers, authors, owners, and tenant administrators can inspect traces for services they may view. Runtime clients use `GET /api/runtime/v1/decisions/{serviceKey}/traces/{evaluationId}` with the same service API key that created the trace. A different key receives the same non-disclosing not-found response as an unknown trace.

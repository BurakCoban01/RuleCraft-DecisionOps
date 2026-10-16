# Stable decision-content identity

Status: Accepted

## Context

Immutable versions hash semantic content, excluding layout coordinates and mutable source revisions. Java immutable maps do not guarantee member iteration order across JVM processes. Hashing their ordinary JSON serialization can therefore report drift after a restart even when the model has not changed.

## Decision

Recursively sort JSON object members before hashing new semantic content. Preserve array order: table-rule precedence and other ordered collections remain meaningful. Do not change the application-wide JSON mapper or decision evaluation semantics.

For an unchanged latest snapshot, compare the working content structurally with its persisted semantic content and retain that snapshot's existing fingerprint. This preserves legacy version, promotion, deployment, request and trace identities without rewriting immutable evidence. A real content change receives a newly canonicalized hash.

## Compatibility and verification

No schema or data migration is required. Existing snapshot JSON and fingerprints remain untouched. Capture still checks source revisions and the complete content identity, rejects unchanged candidates and preserves idempotent retries. Tests cover nested member reordering, order-sensitive arrays, changed values and legacy identity retention; persisted PostgreSQL verification additionally checks unchanged governed identity across an API restart.

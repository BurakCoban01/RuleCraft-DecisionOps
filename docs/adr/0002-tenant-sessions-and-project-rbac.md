# ADR 0002 — Tenant-qualified sessions and project RBAC

- Status: Accepted
- Date: 2026-08-29
## Context

RuleCraft needs private multi-tenant identity and project boundaries before decision assets exist. Tenant identifiers supplied in request bodies would create an insecure authorization boundary, stateless credentials would complicate the private browser workbench, and workspace-wide roles alone would not express project ownership.

## Decision

- A tenant has a stable UUID and a human-entered unique workspace slug.
- Login is qualified by workspace slug plus normalized email. Passwords use BCrypt strength 12.
- Spring Security owns authentication and CSRF enforcement; Spring Session persists opaque browser sessions in PostgreSQL. The serialized principal carries identifiers and authorities but not the password hash.
- Every service derives `tenantId` from the authenticated principal. Tenant IDs are never accepted as mutation input.
- Workspace roles are `ADMIN` and `MEMBER`. Project roles are `OWNER`, `EDITOR`, `REVIEWER`, and `VIEWER`.
- Any active workspace user may create a project and becomes its first owner. Workspace administrators can administer all tenant projects. Otherwise, project membership controls visibility and only owners administer membership.
- Membership mutations serialize on the project row, and the final owner cannot be demoted or removed.
- Administrative and project mutations write tenant-scoped audit events in the same transaction.
- Initial administration is an explicit, disabled-by-default, idempotent environment bootstrap. No default credentials are stored in source.

## Consequences

The API performs an account-status lookup on authenticated requests so disabling an account takes effect on its next request. Project and user queries must always carry tenant scope. Session cleanup, federated identity, MFA, recovery, cluster-wide login throttling, and expanded audit browsing remain security-hardening work for operational security; this design does not claim those capabilities.

# Identity and project access

RuleCraft opens with a private workspace sign-in using a workspace handle, email, and password. A successful session reveals only projects visible to that account.

## Roles

- Workspace administrators provision and disable accounts, create projects, and administer every project in their tenant.
- Workspace members can create projects and become owners of what they create.
- Project owners manage membership. Editors, reviewers, and viewers receive progressively narrower authoring permissions as later decision capabilities arrive.

Tenant context is established by the authenticated session, not by editable UI fields or request payloads. Cross-tenant project identifiers return the same not-found response as missing projects.

## Visible states

The access experience includes platform and session loading, anonymous sign-in, invalid-credential feedback, offline/service recovery, empty project catalog, project creation validation/conflict feedback, project membership loading and errors, administrator-only team access, disabled-user status, direct-route permission feedback, responsive mobile navigation, and light/dark themes.

An administrator enables the first workspace account through the documented environment bootstrap. Bootstrap is disabled by default and never embeds a password in the frontend or repository.

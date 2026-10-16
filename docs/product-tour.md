# Synthetic retail-lending workspace

The README images are real browser captures of RuleCraft backed by PostgreSQL. The fictional `northstar-rules` workspace contains **Retail Lending Decisioning**, a reusable loan-application model and four executable services. No customer data or populated database is distributed.

Two smaller related projects, **SME Lending Policy** and **Card Limit Management**, each contain a validated, executable five-node authoring model. They demonstrate project scope and working-capital / revolving-limit rules; the full governed lifecycle belongs to the primary retail-lending service.

This is an illustrative policy, not underwriting guidance, regulatory certification, production traffic or capacity evidence. All accounts use `.test` addresses. Deniz Operator has the implemented `VIEWER` project role; the product does not invent an operator permission tier.

## Reproduce locally

Use a **dedicated disposable database**, not an existing business workspace. Follow [native development setup](operations.md#native-java--node-development) with these bootstrap values before starting the API:

| Environment variable | Value |
|---|---|
| `RULECRAFT_BOOTSTRAP_ENABLED` | `true` for first initialization only |
| `RULECRAFT_BOOTSTRAP_WORKSPACE` | `northstar-rules` |
| `RULECRAFT_BOOTSTRAP_TENANT_NAME` | `Northstar Policy Studio` |
| `RULECRAFT_BOOTSTRAP_ADMIN_EMAIL` | `ada@northstar.test` |
| `RULECRAFT_BOOTSTRAP_ADMIN_NAME` | `Ada Admin` |
| `RULECRAFT_BOOTSTRAP_ADMIN_PASSWORD` | A new locally generated password, at least 12 characters |

Supply the database password through the normal environment contract. Start PostgreSQL with Compose, the API with Java 25, and the web application with Node 24 / pnpm 10. The defaults are the existing loopback API and web ports. Build/package the API or use the documented Maven development command; no separate fixture runtime is required.

After bootstrap, disable it and remove its password from the API environment. In the terminal used for the following commands, supply `RULECRAFT_SHOWCASE_EMAIL=ada@northstar.test` and `RULECRAFT_SHOWCASE_PASSWORD` with that local administrator password. Do not place credentials in command-line arguments, screenshots or committed files.

From `web/`:

```sh
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
pnpm showcase:test
pnpm showcase:populate --allow-local-writes
pnpm showcase:check
pnpm showcase:capture
```

Use `RULECRAFT_SHOWCASE_ORIGIN` to override `http://127.0.0.1:50111`, for example when inspecting a local container edge. Only HTTP(S) loopback hosts are accepted. Population additionally requires the dedicated workspace, synthetic credentials and the explicit write flag. It uses sessions, CSRF tokens, RBAC, revision guards and normal application APIs; it never writes application tables directly or adds a production endpoint.

The commands reuse existing named fixture objects and deterministic retry identifiers. Repeating a completed population does not create extra versions, reviews, deployments, scenarios or evaluation traffic. The workspace is reserved for this fixture: use a fresh disposable database if exploring incompatible changes. Reusing old users requires their original local password; the utility does not reset accounts to bypass authentication.

Captures default to ignored `web/test-results/showcase/`. They are not automatically copied into documentation. Review images before publishing them. `RULECRAFT_SHOWCASE_WIDTH=1440` selects 1440×900; the default is 1600×1000. Compact widths use 740px height. `RULECRAFT_SHOWCASE_ONLY` accepts comma-separated image names when reviewing only an affected surface. An optional `RULECRAFT_SHOWCASE_CAPTURE_DIR` changes the output directory. Optional JSON reports should also stay in ignored local output.

The capture workflow signs in through the UI, visits persisted routes, waits for data/fonts/paint, uses actual product controls, and checks loading/error/overflow state. It does not replace data, inject presentation values, hide elements or composite screenshots.

## What the workflow creates

| Area | Persisted content |
|---|---|
| Access | Ada Admin; Elif Author (`EDITOR`); Mert Reviewer (`REVIEWER`); Deniz Operator (`VIEWER`) |
| Types | Applicant profile, credit profile, financial position, loan request, loan application; employment, residency, purpose and channel enums |
| Services | `LOAN_OFFER`, `APPLICATION_ELIGIBILITY`, `CREDIT_RISK_CLASSIFICATION`, `AFFORDABILITY_ASSESSMENT` |
| Main graph | One structured input, six decisions, two outputs, twelve exact-type connections |
| Implementations | Five RCEL-1 expressions and ten ordered `FIRST` table rules |
| Assurance | Eighteen boundary cases and three completed 18/18 regression runs |
| Governance | Three behaviorally distinct versions, submitted by an editor, approved by a reviewer and promoted by the owner |
| Activation | Three initial activations, rollback to the prior policy, then restoration of the reviewed policy |
| Runtime | 61 evaluations across sandbox, single API, API batch and workbench batch paths; mixed approval, decline and referral decisions |
| Operations | Trace-derived analytics, dependency assessment and one clean recovery scan with zero repairs |

The temporary service API credential is revoked after integration traffic. Its one-time secret stays in memory and is never captured. Trace checks preserve boolean evidence and assert redaction of other intermediate values. The first rollback probe adds real evidence against the restored artifact; subsequent traffic uses the final reviewed version.

## Review and validation

`showcase:check` verifies real persisted counts, role-separated review events, distinct version identities, regression results, rollback history, every main workbench, fitted node bounds, typed-edge endpoints, keyboard viewport controls, light/dark themes and widths on both sides of the navigation breakpoint. It must not mutate the graph while inspecting it.

The same workflow is exercised against a clean PostgreSQL workspace in CI. Backend correctness/security tests and the complete existing desktop/compact browser acceptance suite remain separate gates; the fixture is not a substitute for those tests.

Use the [testing guide](testing.md) for the full suite and its accessibility/performance evidence limits. No representative-user study, real screen-reader session, production capacity or financial suitability is implied by these screenshots.

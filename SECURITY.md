# Security

RuleCraft uses server-enforced tenant/project scope, role-aware mutations, explicit runtime contracts, and private network defaults. These controls support review and deployment planning; they do not replace an environment-specific security assessment.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting for this repository when that option is available in the Security tab. Otherwise contact the repository owner through the contact details on their GitHub profile. Do not place credentials, personal data, or exploit payloads containing sensitive values in public issues or pull requests.

The active development line is `main`. There is no separate long-term support or response-time commitment.

## Authentication and authorization

- Passwords are BCrypt hashed. Browser sessions live in PostgreSQL and use session-fixation protection. Authenticated requests recheck account status.
- Tenant identity comes from the authenticated principal. Project access combines tenant scope and explicit `OWNER`, `EDITOR`, `REVIEWER`, or `VIEWER` memberships; administrators can manage their tenant.
- Browser writes require CSRF tokens. Production session cookies default to Secure, HTTP-only, and SameSite Strict.
- `/api/runtime/v1` uses a separate stateless security chain. Service credentials use cryptographically random values, are shown once, stored as SHA-256 digests, scoped to a service, and revocable.
- Review, promotion, activation, and recovery have distinct role requirements. Role separation is enforced, but the product does not mandate a second distinct human: owners and administrators may hold multiple permissions.
- Cross-tenant identifiers produce scoped not-found responses rather than revealing another tenant's objects.

## Expression and request boundaries

RCEL-1 is parsed and evaluated by owned Java code. It cannot call Java, JavaScript, reflection, methods, the filesystem, environment variables, processes, or the network. Functions come from a fixed allowlist. Input and expression budgets constrain work; the expression language is documented in [the language reference](docs/expression-language.md).

Tables, graphs, scenarios, regression selections, batches, and benchmarks have explicit size bounds. Runtime requests carry idempotency IDs, canonical request hashes, and active-artifact identities. Reusing an ID with different content returns a conflict. Stable problem codes and bounded messages avoid exposing stack traces.

Application responses include restrictive browser headers. nginx limits request bodies and hides actuator/OpenAPI at the production edge. Internal readiness and liveness probes remain separately accessible to the container runtime.

## Data and privacy

| Record | Retained information |
|---|---|
| Accounts / sessions | Account identifiers, BCrypt password hash, session state |
| Authoring / versions | User-authored rules, contracts, scenarios, captured artifacts and actor metadata |
| Test and regression results | Expected and actual business values with model/test revisions |
| Runtime request evidence | Request hash, response JSON for exact retries, deployment/version identity, counts and time |
| Decision traces | Field names, payload fingerprints, implementation/matched-rule steps, status, duration; boolean results may remain visible, other results are redacted |
| Audit / operations | Action, actor, target identity, bounded operational metadata and recovery history |

Trace redaction does not mean that all database records are payload-free. Test scenarios and idempotent responses can retain business data. Choose synthetic data for demonstrations, restrict database access, define retention/deletion procedures, encrypt disks/backups, and assess whether fingerprints or field names are sensitive for the intended use case. The application does not provide a complete privacy-retention policy or automatic off-host encryption.

## Deployment requirements

- Keep API, management, and PostgreSQL ports private. The supplied Compose model publishes only the loopback web edge.
- Run the exposure verifier after rendering real deployment configuration.
- Use TLS at the ingress and retain Secure session cookies. The HTTP cookie override is only for an isolated loopback demo.
- Generate unique credentials; enable bootstrap only for first initialization, then disable it and remove its password.
- Use a secret manager or protected host environment. `.env.example` files contain placeholders; populated environment files, dumps, logs, and browser artifacts must remain untracked.
- Add shared rate limits for multiple replicas. The built-in login/runtime counters are process-local and reset on restart.
- Add external monitoring, alerting, backup retention, restore drills, dependency maintenance, and access-log redaction appropriate to the target environment.
- Images run without root, drop capabilities, disable privilege escalation, and use read-only application roots and bounded temporary storage.

## Test fixtures

Accounts under `example.test` / `other.test`, short mock API values, and passwords inside test sources are deterministic synthetic fixtures. They are never production defaults. The production bootstrap is disabled unless explicitly enabled with operator-supplied values. Never reuse fixture credentials in a persistent environment.

See [testing](docs/testing.md), [operations](docs/operations.md), and the [backup/restore runbook](docs/operations/backup-restore-runbook.md) for reproducible verification and recovery procedures.

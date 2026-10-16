# Testing

RuleCraft combines domain tests, Spring Boot integration tests, React component tests, real PostgreSQL verification, and browser acceptance. Tests use synthetic accounts and decision data. Runtime or database credentials from these fixtures must not be reused outside disposable test environments.

## Fast local checks

Java 25 and Maven 3.9 are enforced by the wrapper build. Node.js 24 and pnpm 10 are declared in the frontend manifest.

```sh
cd api
./mvnw verify
cd ../web
pnpm install --frozen-lockfile
pnpm lint
pnpm test
pnpm build
```

Windows uses `mvnw.cmd`. Frontend tests run one worker by default. Keep Maven, frontend builds, and browser execution sequential on machines with limited memory.

The default backend test configuration uses H2 in PostgreSQL compatibility mode for a short feedback loop. That is not sufficient proof of PostgreSQL behavior; run the database profile below as well.

## What the tests protect

| Area | Important invariants |
|---|---|
| Identity / projects | Database-backed sessions, invalid login, cross-tenant isolation, last owner, role checks |
| Catalog / graph | Type references, cycles, exact-type connections, contract drift, optimistic revisions |
| Tables / expressions | Rule shape, hit policies, typed literals, grammar, diagnostics, unsafe functions, implementation exclusivity |
| Runtime | Required and unknown fields, deterministic outputs, stable failures, captured artifacts |
| Assurance | Scenario snapshots, expected/actual comparison, matrix checkpoints and interruption recovery |
| Governance | Immutable versions, semantic changes, stale candidates, allowed transitions, promoted baseline |
| Decision API | Hashed/revoked keys, exact retries, changed reuse, concurrent convergence, ordered batch failures |
| Explainability | Scoped access, payload fingerprints, redacted results, immutable trace steps |
| Operations | Impact identities, bounded analytics/benchmarks, rate controls, posture, recovery and preserved history |

Coverage is defined by assertions and scenarios, not by a claimed percentage. The repository does not claim production load capacity or full DMN conformance.

## PostgreSQL integration

Start an isolated PostgreSQL database, supply its JDBC connection values, and run the backend with the `postgres` test profile. The profile runs the same integration contracts against PostgreSQL rather than changing what they assert.

```sh
export RULECRAFT_TEST_DB_URL=jdbc:postgresql://127.0.0.1:50112/rulecraft
export RULECRAFT_TEST_DB_USER=rulecraft
export RULECRAFT_TEST_DB_PASSWORD='<your disposable test database password>'
cd api
./mvnw verify -Dspring.profiles.active=postgres
```

Use a disposable database: the tests create tenants, users, models, versions, scenarios, and runtime evidence. Flyway applies the real SQL migrations and Hibernate validates the schema. CI supplies an isolated PostgreSQL service.

In PowerShell, quote the JVM property argument: `.\mvnw.cmd verify '-Dspring.profiles.active=postgres'`.

## Browser acceptance

Run the native development API and web server against a clean database. For this disposable test environment only, initialize workspace `acme-rules`, administrator `admin@example.test`, and the fixture password from `api/src/test/resources/application.yml`. Never put that fixture identity in a persistent deployment.

```sh
cd web
pnpm exec playwright install chromium
pnpm test:e2e
```

Feature journeys cover sign-in, theme/recovery, catalog/type creation, graph/table/expression authoring, execution, scenarios, regressions, semantic versions, approval/promotion, runtime activation, single/batch API, traces, dependency impact, analytics, and Operations. They assert persisted state after reload and check unexpected network failures, console errors, and horizontal overflow at 1440×900 and 360×740.

The final portfolio checks run after the feature journeys through explicit Playwright project dependencies, so their persisted-volume/recovery assertions do not depend on filename ordering. Screenshots and failure traces go to ignored test output; generated test artifacts are not committed.

Production-edge acceptance is separate because it verifies the hardened nginx headers and hidden management/OpenAPI routes. Set `RULECRAFT_E2E_PRODUCTION=true` and run `pnpm test:e2e` against the loopback container edge; only the two deployment-readiness scenarios are selected. `RULECRAFT_E2E_BASE_URL` can override the edge URL. See [operations](operations.md) for the production-shaped smoke procedure.

## CI gates

The GitHub workflow verifies the backend, frontend, real PostgreSQL contracts, documentation/publication boundaries, secret history, Compose configuration, hardened container builds, and a small runtime smoke path. It uses frozen dependency installation, immutable action SHAs, bounded job timeouts, and no `continue-on-error` escape.

CI status describes the exact pushed commit. A clean clone should repeat the documented commands before a release; cached local success is not a substitute for that check.

## Limits of this evidence

Automated semantic/keyboard assertions, cognitive walkthroughs, and desktop/compact visual review support accessibility validation. They are not equivalent to a representative-user study or a real screen-reader session. Those remain appropriate release checks for a target audience and browser/platform set.

Engine microbenchmarks are bounded sequential diagnostics that exclude HTTP, persistence, tracing, network, and concurrency. They do not establish a throughput SLO or production capacity.

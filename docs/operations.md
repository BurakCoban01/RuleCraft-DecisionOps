# Operations

The supplied configuration supports local development and a production-shaped private container rehearsal. It does not provision infrastructure or deploy to a hosting provider.

## Local container run

Follow [the README](../README.md#getting-started) with Docker Compose. The default edge is `127.0.0.1:50113`; PostgreSQL and both API ports remain private. API and web images run without root privileges, use read-only roots and bounded temporary storage, drop Linux capabilities, and expose health checks.

Build images sequentially on constrained hardware. Allow several GB of free disk for images, dependencies, and a database volume; check memory before builds or browser runs. Do not prune shared caches or unrelated Docker volumes to make a demo fit.

For TLS ingress, immutable build versions, forwarded headers, secure cookies, readiness, upgrades, and rollback constraints, use [the deployment contract](operations/deployment-readiness.md).

## Native Java / Node development

Requirements: Java 25, Node.js 24, pnpm 10, and Docker Compose for PostgreSQL. Copy `.env.example` to `.env`, choose a unique database password, and start the database:

```sh
docker compose --env-file .env up -d db
```

Export the values used by the API. The Java process does not automatically load Compose's `.env` file.

```sh
export RULECRAFT_DB_PASSWORD='<the database password from .env>'
export RULECRAFT_BOOTSTRAP_ENABLED=true
export RULECRAFT_BOOTSTRAP_WORKSPACE=acme-rules
export RULECRAFT_BOOTSTRAP_TENANT_NAME='Acme Rules'
export RULECRAFT_BOOTSTRAP_ADMIN_EMAIL=admin@example.test
export RULECRAFT_BOOTSTRAP_ADMIN_NAME='Local Administrator'
export RULECRAFT_BOOTSTRAP_ADMIN_PASSWORD='<a unique password of at least 12 characters>'
cd api
./mvnw spring-boot:run
```

In PowerShell, use `$env:NAME = 'value'` and `.\mvnw.cmd spring-boot:run`. In another terminal, run `pnpm install --frozen-lockfile` and `pnpm dev` in `web/`. Open `http://127.0.0.1:50111`; the client proxies to the API on `50110`. PostgreSQL binds to loopback `50112`.

After the first successful sign-in, disable bootstrap and remove its password from the environment. Bootstrap is idempotent but should not remain an active operational mechanism.

## Readiness and smoke checks

- `docker compose ... ps` must show all services healthy.
- The web edge's `/healthz` returns 200. `/actuator/health` and `/v3/api-docs` return 404 at that edge.
- Inside the API container, the separate management port reports liveness/readiness without exposing sensitive details.
- Sign-in, project loading, and Operations show the authoritative schema and readiness state.
- Verify a valid captured version, current promoted baseline, active-artifact evaluation, and one single/batch Decision API request where the environment contains that data.

Render and verify production configuration before traffic:

```powershell
docker compose -f compose.production.yaml --env-file .env.production config --quiet
.\scripts\verify-private-exposure.ps1 -EnvFile .env.production
```

The verifier rejects public database/API ports, non-loopback web publication, a non-internal database network, privileged services, or weakened application container hardening.

For a disposable loopback rehearsal, export `RULECRAFT_SMOKE_WORKSPACE`, `RULECRAFT_SMOKE_EMAIL`, and `RULECRAFT_SMOKE_PASSWORD` with the configured administrator identity, then run `python scripts/smoke_test.py`. The check signs in, creates a synthetic project, verifies its persisted presence and the V16 operational posture, and signs out. It intentionally leaves that project in the disposable database; do not run it against business data. Credentials remain in the process environment and are not printed.

## Recovery

Operations is administrator-only. Review its posture and fingerprint before running a recovery scan. The scan can interrupt `RUNNING` regression matrices and clear inconsistent runtime pointers while retaining governed history. Coordinate with active work first: this application does not maintain distributed worker leases.

The scan is idempotent and stale guarded, but it is not a database backup. Use the [backup/restore runbook](operations/backup-restore-runbook.md) for custom-format archives, SHA-256 sidecars, exact database confirmation, active-API refusal, and post-restore validation. Never repair an incident by editing Flyway history.

For application rollback, activate an eligible prior immutable artifact. For a binary/schema rollback, verify compatibility with the current database or restore a matching pre-upgrade backup while the API is stopped. Forward migrations are not automatically reversible.

## Safe stop

```sh
docker compose -f compose.production.yaml --env-file .env.local stop web api db
```

Do not add volume-removal flags to routine stop commands. The named PostgreSQL volume is the durable authority. Keep backups in protected storage outside the repository and copy verified archives to encrypted off-host retention.

## Common failures

| Symptom | Check / recovery |
|---|---|
| Maven rejects Java | Verify `JAVA_HOME` and `java -version` both select Java 25 |
| Frontend engine mismatch | Use Node.js 24 and the pinned pnpm version |
| Database fails to start | Check required password, free disk, and selected port |
| API cannot authenticate to PostgreSQL | Export the same credentials supplied to Compose; changing an env file does not reset an existing database password |
| Sign-in works over HTTP but session disappears | Use the local-only insecure-cookie override for loopback HTTP; use Secure cookies under TLS |
| Stale save, version, or activation | Refresh and review the newer state; do not bypass revision/fingerprint checks |
| Migration validation fails | Use the matching application/database version; do not delete the migration history |
| Rate-limited request | Honor retry metadata and inspect the caller; shared limits are required across replicas |

## Operator responsibilities

The repository supplies production-oriented configuration and verification, not certificates, DNS, public ingress, a registry, a secret manager, off-host backup storage, or external monitoring. Review [security](../SECURITY.md) and the [release checklist](operations/release-checklist.md) for the intended environment before accepting traffic.

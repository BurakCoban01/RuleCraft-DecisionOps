# Private deployment readiness

This runbook validates a private production-shaped RuleCraft stack. It does not authorize deployment, image push, public binding, tunnels, DNS, or provider creation.

## Artifacts and topology

- `api/Dockerfile` builds with Temurin 25.0.4 and runs the executable jar as UID/GID 10001 on ports 8080/8081.
- `web/Dockerfile` builds with Node 24.19/pnpm 10.29 and serves through nginx 1.31.4 as an unprivileged user on port 8080.
- `compose.production.yaml` runs `web -> api -> PostgreSQL`. Only web is host-published, on `127.0.0.1:50113` by default. API and database have no host ports; the database network is internal.
- `.env.production.example` documents every operator-owned value. Never commit its populated copy.

PostgreSQL is the only durable dependency. No Redis, object store, broker, search service, external telemetry system, or Vercel project is required.

## Build and private start

1. Confirm at least 5 GB disk is free and enough memory exists for one heavy process.
2. Copy `.env.production.example` to `.env.production`. Replace the database password with at least 32 random characters and set an immutable build version.
3. Keep `RULECRAFT_BIND_ADDRESS=127.0.0.1`. Keep `RULECRAFT_SESSION_COOKIE_SECURE=true` whenever traffic reaches RuleCraft through TLS.
4. For only the first controlled start, enable bootstrap and provide a unique administrator password. Disable bootstrap and remove its password immediately after sign-in succeeds.
5. Render and scan before starting:

   ```powershell
   docker compose -f compose.production.yaml --env-file .env.production config --quiet
   .\scripts\verify-private-exposure.ps1 -EnvFile .env.production
   ```

6. Build one image at a time on constrained hardware:

   ```powershell
   docker compose -f compose.production.yaml --env-file .env.production build api
   docker compose -f compose.production.yaml --env-file .env.production build web
   ```

7. Start the private stack and wait for dependencies:

   ```powershell
   docker compose -f compose.production.yaml --env-file .env.production up -d --no-build --wait --wait-timeout 360
   ```

The API’s 180-second health start period accommodates cold starts on the documented constrained host but marks healthy as soon as readiness succeeds.

## Acceptance before traffic

- `docker compose ... ps` shows all services healthy.
- `http://127.0.0.1:50113/healthz` returns 200; `/actuator/health` and `/v3/api-docs` return 404 at the edge.
- The root response includes HSTS, `nosniff`, frame denial, no-referrer, and restrictive permissions policy headers.
- Inside the API container, `/actuator/health/liveness` and `/actuator/health/readiness` both return `UP` on port 8081.
- PostgreSQL reports Flyway V16. Sign-in, Projects, Operations `READY`, and direct reload succeed through the web edge.
- `docker inspect` confirms API UID 10001, read-only application roots, no-new-privileges, and only loopback web publication.

## TLS ingress contract

Place an independently managed TLS reverse proxy on the same private host/network and proxy to loopback web port 50113. Preserve `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto=https`. Do not proxy API port 8080, management port 8081, or PostgreSQL. Apply a shared ingress rate limit because operational security’s application counters are process-local. Keep body size at or below 1 MB unless an reviewed feature requires otherwise.

## Upgrade and rollback

1. Produce a verified backup and record current image digests, build version, schema version, and Operations fingerprint.
2. Build the new immutable tags; never reuse an existing production tag.
3. Stop traffic, then update API before web. Flyway migrations are forward-only and execute at API startup.
4. Verify readiness, V16/current expected migration, login, Operations posture, active deployment evaluation, and Decision API behavior before reopening traffic.
5. Application rollback is safe only when the previous binary accepts the current schema. If a migration is not backward compatible, restore the matching pre-upgrade database backup with the API stopped instead of manually editing Flyway history.

## Safe stop

```powershell
docker compose -f compose.production.yaml --env-file .env.production stop web api db
```

Do not add `-v` to a production stop. Removing the named PostgreSQL volume permanently deletes the authoritative database.

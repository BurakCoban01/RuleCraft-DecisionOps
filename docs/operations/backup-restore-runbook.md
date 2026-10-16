# PostgreSQL backup and restore runbook

## Backup

Keep the stack healthy and run:

```powershell
.\scripts\backup-postgres.ps1 -EnvFile .env.production -OutputDirectory backups
```

The script runs `pg_dump` inside the exact Compose database service, writes a custom-format archive, copies it to the chosen operator directory, emits a SHA-256 sidecar, and removes only its exact container temporary file. It does not stop the application or delete old backups.

Copy both files to encrypted off-host storage with an operator-defined retention policy. A local archive in the ignored `backups/` directory is not disaster recovery.

## Restore prerequisites

- Identify the exact target host, Compose project, database name, build version, and backup timestamp.
- Verify the archive and sidecar are available and preserve the pre-restore database separately when incident policy requires it.
- Stop API traffic and the API service. Leave PostgreSQL running.
- Announce that all database changes after the archive timestamp will be lost.

## Restore

```powershell
docker compose -f compose.production.yaml --env-file .env.production stop api
.\scripts\restore-postgres.ps1 `
  -BackupFile .\backups\rulecraft-YYYYMMDDTHHMMSSZ.dump `
  -ExpectedDatabase rulecraft `
  -ConfirmDatabaseReset `
  -EnvFile .env.production
```

The script refuses to run while API is active, checks the exact target database name, verifies the sidecar when present, restores with `--clean --if-exists --no-owner --no-acl`, and removes only its exact container temporary file. If restore fails, keep the API stopped and inspect PostgreSQL; do not start against a partial state.

## Post-restore validation

1. Start API with the same immutable image and wait for readiness.
2. Confirm Flyway history is at the expected version and Hibernate validation completes.
3. Sign in with a known administrator and verify tenant/project membership.
4. Open Operations and confirm authoritative database/schema checks and the expected posture fingerprint or explain any recoverable degradation.
5. Verify one promoted runtime deployment, one typed sandbox evaluation, one Decision API evaluation, and trace retrieval if those records should exist at the backup time.
6. Reopen traffic only after data owners accept the restored point.

## Failure symptoms

- Checksum mismatch: do not restore; obtain an intact archive.
- API-running refusal: stop API and confirm all replicas are drained.
- Target mismatch: correct `-ExpectedDatabase`; never weaken the check.
- Flyway validation failure: use the application image matching the backup or follow an approved forward migration. Never delete or edit Flyway history manually.
- Low disk: stop before creating another archive and move verified backups off-host. Never prune global Docker volumes or shared caches.

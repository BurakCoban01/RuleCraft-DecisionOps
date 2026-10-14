[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [Parameter(Mandatory = $true)][string]$ExpectedDatabase,
    [Parameter(Mandatory = $true)][switch]$ConfirmDatabaseReset,
    [string]$ComposeFile = "compose.production.yaml",
    [string]$EnvFile = ".env.production"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $root $ComposeFile
$envPath = Join-Path $root $EnvFile
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not $ConfirmDatabaseReset) { throw "Restore is destructive. Pass -ConfirmDatabaseReset after verifying the target and backup." }
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) { throw "Compose file not found: $composePath" }
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw "Environment file not found: $envPath" }

$running = @(& docker compose -f $composePath --env-file $envPath ps --status running --services)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the production stack." }
if ($running -contains "api") { throw "Stop the API before restore so no session or write can race the database reset." }
if ($running -notcontains "db") { throw "The PostgreSQL service must be running for restore." }

$database = (& docker compose -f $composePath --env-file $envPath exec -T db printenv POSTGRES_DB).Trim()
if ($LASTEXITCODE -ne 0 -or $database -ne $ExpectedDatabase) {
    throw "Target mismatch. Running database '$database' does not equal -ExpectedDatabase '$ExpectedDatabase'."
}

$checksumFile = "$resolvedBackup.sha256"
if (Test-Path -LiteralPath $checksumFile -PathType Leaf) {
    $expectedHash = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $resolvedBackup -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) { throw "Backup checksum verification failed." }
}

$archiveName = "restore-$([Guid]::NewGuid().ToString('N')).dump"
$containerPath = "/tmp/$archiveName"
try {
    & docker compose -f $composePath --env-file $envPath cp $resolvedBackup "db:$containerPath"
    if ($LASTEXITCODE -ne 0) { throw "Copying the backup into PostgreSQL failed." }
    & docker compose -f $composePath --env-file $envPath exec -T db sh -ec "pg_restore -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" --clean --if-exists --no-owner --no-acl '$containerPath'"
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed. Keep the API stopped and inspect PostgreSQL before retrying." }
    Write-Output "Restore completed for database '$database'. Start the API and verify readiness, schema V16, sign-in, and the Operations posture before reopening traffic."
}
finally {
    & docker compose -f $composePath --env-file $envPath exec -T db rm -f -- $containerPath 2>$null | Out-Null
}

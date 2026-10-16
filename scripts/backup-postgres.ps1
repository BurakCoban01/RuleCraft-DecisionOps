[CmdletBinding()]
param(
    [string]$ComposeFile = "compose.production.yaml",
    [string]$EnvFile = ".env.production",
    [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $root $ComposeFile
$envPath = Join-Path $root $EnvFile
$destinationDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $root $OutputDirectory }
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) { throw "Compose file not found: $composePath" }
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw "Environment file not found: $envPath" }
New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null

$archiveName = "rulecraft-$([DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')).dump"
$containerPath = "/tmp/$archiveName"
$destination = Join-Path $destinationDirectory $archiveName

try {
    & docker compose -f $composePath --env-file $envPath exec -T db sh -ec "pg_dump -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" --format=custom --no-owner --no-acl -f '$containerPath'"
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed." }
    & docker compose -f $composePath --env-file $envPath cp "db:$containerPath" $destination
    if ($LASTEXITCODE -ne 0) { throw "Copying the backup from PostgreSQL failed." }
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$destination.sha256" -Value "$hash  $archiveName" -Encoding ascii
    Write-Output "Backup created: $destination"
    Write-Output "SHA-256: $hash"
}
finally {
    & docker compose -f $composePath --env-file $envPath exec -T db rm -f -- $containerPath 2>$null | Out-Null
}

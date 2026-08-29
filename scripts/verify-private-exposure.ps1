[CmdletBinding()]
param(
    [string]$ComposeFile = "compose.production.yaml",
    [string]$EnvFile = ".env.production.example"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $root $ComposeFile
$envPath = Join-Path $root $EnvFile
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) { throw "Compose file not found: $composePath" }
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw "Environment file not found: $envPath" }

$json = & docker compose -f $composePath --env-file $envPath config --format json
if ($LASTEXITCODE -ne 0) { throw "Docker Compose configuration is invalid." }
$config = $json | ConvertFrom-Json
$problems = [System.Collections.Generic.List[string]]::new()

foreach ($property in $config.services.PSObject.Properties) {
    $name = $property.Name
    $service = $property.Value
    $ports = if ($null -eq $service.ports) { @() } else { @($service.ports) }
    if ($name -ne "web" -and $ports.Count -gt 0) { $problems.Add("$name must not publish a host port.") }
    foreach ($port in $ports) {
        if ($port.host_ip -notin @("127.0.0.1", "::1")) {
            $problems.Add("$name publishes $($port.published) on non-loopback address '$($port.host_ip)'.")
        }
    }
    if ($service.privileged -eq $true) { $problems.Add("$name must not run privileged.") }
    if ($name -in @("api", "web")) {
        if ($service.read_only -ne $true) { $problems.Add("$name must use a read-only root filesystem.") }
        if (@($service.cap_drop) -notcontains "ALL") { $problems.Add("$name must drop all Linux capabilities.") }
        if (@($service.security_opt) -notcontains "no-new-privileges:true") { $problems.Add("$name must disable privilege escalation.") }
    }
}

if ($config.networks.data.internal -ne $true) { $problems.Add("The database network must remain internal.") }
if ($problems.Count -gt 0) { throw ($problems -join [Environment]::NewLine) }
Write-Output "Private-exposure verification passed: only web is published, on loopback, and application containers are hardened."

<#
.SYNOPSIS
  Starts the local Locator stack and waits until every service is healthy.
.PARAMETER NoBuild
  Skip the image build.
#>
[CmdletBinding()]
param(
    [switch]$NoBuild
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

$root = Get-RepoRoot
$envPath = Join-Path $root 'infra\.env'
$examplePath = Join-Path $root 'infra\.env.example'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "infra\.env not found. Run: Copy-Item infra\.env.example infra\.env, then fill in every empty value."
}
$missing = @(Get-MissingEnvNames -EnvPath $envPath -ExamplePath $examplePath)
if ($missing.Count -gt 0) {
    throw ("infra\.env is missing or has empty values for: " + ($missing -join ', ') + ". Nothing was started.")
}

$settings = Read-DotEnv -Path $envPath
foreach ($name in @('LOCATOR_HTTP_PORT', 'LOCATOR_DB_PORT')) {
    $port = 0
    if (-not [int]::TryParse([string]$settings[$name], [ref]$port)) {
        throw "$name must be a port number, got '$($settings[$name])'."
    }
    $owner = ''
    if (-not (Test-PortUsable -Port $port -Owner ([ref]$owner))) {
        throw "$name=$port is already in use by $owner. Change the port in infra\.env (and LOCATOR_PUBLIC_BASE_URL if it is the HTTP port). Nothing was started."
    }
}

$composeArgs = Get-ComposeArgs
$upArgs = @('up', '-d')
if (-not $NoBuild) { $upArgs += '--build' }
$upArgs += @('--wait', '--wait-timeout', '180')

Write-Host 'Starting the Locator stack...'
Invoke-Native -Command 'docker' -Arguments ($composeArgs + $upArgs)

$httpPort = $settings['LOCATOR_HTTP_PORT']
Write-Host ''
Write-Host 'Stack is healthy.'
Write-Host "  Host:      http://localhost:$httpPort"
Write-Host "  Emulator:  http://10.0.2.2:$httpPort"
Write-Host "  Phone:     adb reverse tcp:$httpPort tcp:$httpPort   (install-phone.ps1 does it)"

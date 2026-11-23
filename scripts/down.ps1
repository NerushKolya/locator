<#
.SYNOPSIS
  Stops the local Locator stack. Never removes volumes: stored data survives.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

Invoke-Native -Command 'docker' -Arguments ((Get-ComposeArgs) + @('down', '--remove-orphans'))
Write-Host 'Stack stopped. Data volume kept.'

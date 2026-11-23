# Shared helpers for the developer scripts in scripts/.
# Compatible with Windows PowerShell 5.1 and PowerShell 7: no ??, ?:, && or ||.

Set-StrictMode -Version 2.0

function Get-RepoRoot {
    <# Absolute path of the repository root (parent of scripts/). #>
    $libDir = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $libDir '..\..')).Path
}

function Test-IsWindowsHost {
    <# $IsWindows does not exist in Windows PowerShell 5.1, so fall back to the platform id. #>
    $var = Get-Variable -Name IsWindows -ErrorAction SilentlyContinue
    if ($null -ne $var) {
        return [bool]$var.Value
    }
    return ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT)
}

function Read-DotEnv {
    <# Reads NAME=value lines; ignores comments and blank lines. Returns an ordered dictionary. #>
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )
    $result = New-Object System.Collections.Specialized.OrderedDictionary
    if (-not (Test-Path -LiteralPath $Path)) {
        return $result
    }
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $eq = $trimmed.IndexOf('=')
        if ($eq -lt 1) {
            continue
        }
        $name = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim()
        if ($value.Length -ge 2) {
            $first = $value[0]
            $last = $value[$value.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $result[$name] = $value
    }
    return $result
}

function Invoke-Native {
    <# Runs a native command; throws with the full command line when the exit code is non-zero. #>
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @()
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        $line = (@($Command) + $Arguments) -join ' '
        throw "Command failed with exit code ${LASTEXITCODE}: $line"
    }
}

function Write-CheckLine {
    <# One line per check: OK / WARN / FAIL, the check name and an optional hint. #>
    param(
        [Parameter(Mandatory = $true)][ValidateSet('OK', 'WARN', 'FAIL')][string]$Status,
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Hint = ''
    )
    $color = 'Green'
    if ($Status -eq 'WARN') { $color = 'Yellow' }
    if ($Status -eq 'FAIL') { $color = 'Red' }
    $text = '{0,-4} {1}' -f $Status, $Name
    if ($Hint.Length -gt 0 -and $Status -ne 'OK') {
        $text = $text + ' - ' + $Hint
    }
    Write-Host $text -ForegroundColor $color
}

function Get-ComposeArgs {
    <# Common docker compose arguments: file, env file and fixed project name. #>
    $root = Get-RepoRoot
    return @(
        'compose',
        '-f', (Join-Path $root 'infra\docker-compose.yml'),
        '--env-file', (Join-Path $root 'infra\.env'),
        '-p', 'locator'
    )
}


function Get-MissingEnvNames {
    <# Names from .env.example that are absent or empty in .env. Returns a string array. #>
    param(
        [Parameter(Mandatory = $true)][string]$EnvPath,
        [Parameter(Mandatory = $true)][string]$ExamplePath
    )
    $example = Read-DotEnv -Path $ExamplePath
    $actual = Read-DotEnv -Path $EnvPath
    $missing = @()
    foreach ($name in $example.Keys) {
        if (-not $actual.Contains($name) -or [string]::IsNullOrWhiteSpace([string]$actual[$name])) {
            $missing += $name
        }
    }
    return $missing
}

function Get-LocatorPublishedPorts {
    <# Host ports published by containers of Compose project "locator" (empty when Docker is unavailable). #>
    $ports = @()
    $lines = & docker ps --filter 'label=com.docker.compose.project=locator' --format '{{.Ports}}' 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $ports
    }
    foreach ($line in $lines) {
        foreach ($m in [regex]::Matches([string]$line, ':(\d+)->')) {
            $ports += [int]$m.Groups[1].Value
        }
    }
    return $ports
}

function Get-PortOwner {
    <# Description of the process listening on a TCP port, or $null when the port is free. #>
    param([Parameter(Mandatory = $true)][int]$Port)
    if (Test-IsWindowsHost) {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $conn) { return $null }
        $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        if ($null -eq $proc) { return "pid $($conn.OwningProcess)" }
        return "$($proc.ProcessName) (pid $($proc.Id))"
    }
    $out = & ss -ltnp "sport = :$Port" 2>$null
    $rows = @($out | Select-Object -Skip 1)
    if ($rows.Count -eq 0) { return $null }
    return ([string]$rows[0]).Trim()
}

function Test-PortUsable {
    <# $true when the port is free or published by a Locator container; otherwise returns the owner text via -Owner. #>
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [ref]$Owner
    )
    $found = Get-PortOwner -Port $Port
    if ($null -eq $found) { return $true }
    if ((Get-LocatorPublishedPorts) -contains $Port) { return $true }
    if ($null -ne $Owner) { $Owner.Value = $found }
    return $false
}

Export-ModuleMember -Function Get-RepoRoot, Test-IsWindowsHost, Read-DotEnv, Invoke-Native, Write-CheckLine, Get-ComposeArgs, Get-MissingEnvNames, Get-LocatorPublishedPorts, Get-PortOwner, Test-PortUsable

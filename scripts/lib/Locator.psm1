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
    $code = Invoke-NativeExitCode -Command $Command -Arguments $Arguments
    if ($code -ne 0) {
        $line = (@($Command) + $Arguments) -join ' '
        throw "Command failed with exit code ${code}: $line"
    }
}

function Invoke-NativeExitCode {
    <#
      Runs a native command and returns its exit code without throwing. Progress that tools such as
      docker write to stderr must not stop the script: Windows PowerShell 5.1 turns stderr lines into
      error records when output is redirected, so the preference is relaxed for this call only.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @()
    )
    $ErrorActionPreference = 'Continue'
    & $Command @Arguments 2>&1 | ForEach-Object { Write-Host ([string]$_) }
    return $LASTEXITCODE
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
    <# Common docker compose arguments: file, env file (default infra/.env) and fixed project name. #>
    param([string]$EnvFile = '')
    $root = Get-RepoRoot
    if ($EnvFile.Length -eq 0) {
        $EnvFile = Join-Path $root 'infra\.env'
    }
    return @(
        'compose',
        '-f', (Join-Path $root 'infra\docker-compose.yml'),
        '--env-file', $EnvFile,
        '-p', 'locator'
    )
}

function New-ThrowAwayEnvFile {
    <#
      Writes a temporary env file with every name of infra/.env.example: example values where present,
      a fixed throw-away value where the example is empty. Compose requires these names (":?") even
      when only test profile services run, and CI has no infra/.env. Returns the file path.
    #>
    $root = Get-RepoRoot
    $example = Read-DotEnv -Path (Join-Path $root 'infra\.env.example')
    $lines = @()
    foreach ($name in $example.Keys) {
        $value = [string]$example[$name]
        if ([string]::IsNullOrWhiteSpace($value)) {
            $value = 'throw-away-test-value'
        }
        $lines += "$name=$value"
    }
    $path = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllLines($path, [string[]]$lines)
    return $path
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
function Initialize-AndroidEnvironment {
    <#
      Sets JAVA_HOME (Android Studio runtime, unless already set) and ANDROID_HOME (default SDK location)
      for this process only, so that Gradle and adb work without changing machine settings.
    #>
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (Test-Path -LiteralPath $jbr) {
            $env:JAVA_HOME = $jbr
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path -LiteralPath $sdk) {
            $env:ANDROID_HOME = $sdk
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        throw 'JAVA_HOME is not set and Android Studio runtime was not found. Install Android Studio or set JAVA_HOME to a JDK 17 or newer.'
    }
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        throw 'ANDROID_HOME is not set and the default SDK location was not found. Install the Android SDK with Android Studio.'
    }
}

function Get-AdbPath {
    <# Full path of adb: from PATH, else from the SDK platform-tools. #>
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $cmd) { return $cmd.Source }
    Initialize-AndroidEnvironment
    $path = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $path)) {
        throw 'adb was not found. Add Android SDK platform-tools to PATH.'
    }
    return $path
}

function Get-AdbDevices {
    <# Serial numbers of devices in state "device" (not "offline" or "unauthorized"). #>
    param([Parameter(Mandatory = $true)][string]$Adb)
    $ErrorActionPreference = 'Continue'
    $lines = & $Adb devices 2>&1 | ForEach-Object { [string]$_ }
    $serials = @()
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device\s*$') { $serials += $Matches[1] }
    }
    return $serials
}

Export-ModuleMember -Function Get-RepoRoot, Test-IsWindowsHost, Read-DotEnv, Invoke-Native, Invoke-NativeExitCode, Write-CheckLine, Get-ComposeArgs, New-ThrowAwayEnvFile, Get-MissingEnvNames, Get-LocatorPublishedPorts, Get-PortOwner, Test-PortUsable, Initialize-AndroidEnvironment, Get-AdbPath, Get-AdbDevices

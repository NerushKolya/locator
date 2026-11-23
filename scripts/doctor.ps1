<#
.SYNOPSIS
  Checks the developer environment (automatic rows of docs/ops/env-checklist.md).
  Prints OK / WARN / FAIL per check; exits 1 if any check fails.
  Never starts or stops containers and never installs or changes anything.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Continue'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

$root = Get-RepoRoot
$script:failed = $false

function Add-Check {
    param([string]$Status, [string]$Name, [string]$Hint = '')
    Write-CheckLine -Status $Status -Name $Name -Hint $Hint
    if ($Status -eq 'FAIL') { $script:failed = $true }
}

function Test-Command {
    param([string]$Name)
    return ($null -ne (Get-Command $Name -ErrorAction SilentlyContinue))
}

# Minimum Docker Compose version for "up --wait --wait-timeout".
# Not verified in official release notes: the flag is documented in the CLI reference; 2.20.0 is a conservative choice.
$minCompose = [version]'2.20.0'

# 1. Git and local identity
if (-not (Test-Command 'git')) {
    Add-Check 'FAIL' '1 Git available' 'install Git for Windows'
} else {
    $name = (& git -C $root config --local user.name 2>$null)
    if ([string]::IsNullOrWhiteSpace($name)) {
        Add-Check 'FAIL' '1 Git local identity' 'run: git config --local user.name "<name>" (never --global here)'
    } else {
        Add-Check 'OK' '1 Git local identity'
    }
}

# 2. Line endings in the index
if (Test-Command 'git') {
    $bad = @()
    $eol = & git -C $root ls-files --eol 2>$null
    foreach ($line in $eol) {
        if ($line -match '^i/(crlf|mixed)\s' -and $line -match '\.(sh|kt|kts|yml|yaml|md)$') {
            $bad += $line
        }
    }
    if ($bad.Count -gt 0) {
        Add-Check 'FAIL' '2 Line endings' ("CRLF in the index: " + $bad[0].Split("`t")[-1] + '; check .gitattributes')
    } else {
        Add-Check 'OK' '2 Line endings'
    }
}

# 3. adb
if (Test-Command 'adb') { Add-Check 'OK' '3 adb on PATH' } else { Add-Check 'FAIL' '3 adb on PATH' 'add Android SDK platform-tools to PATH' }

# 4. Android SDK
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platforms'
if (Test-Path -LiteralPath $sdk) { Add-Check 'OK' '4 Android SDK' } else { Add-Check 'FAIL' '4 Android SDK' 'install an SDK platform in Android Studio (SDK Manager)' }

# 5. JDK for Gradle
$gradlew = Join-Path $root 'android\gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew)) {
    Add-Check 'WARN' '5 JDK for Gradle' 'android/ project does not exist yet'
} else {
    $v = (& $gradlew -p (Join-Path $root 'android') -version 2>&1 | Out-String)
    if ($v -match 'JVM:') { Add-Check 'OK' '5 JDK for Gradle' } else { Add-Check 'FAIL' '5 JDK for Gradle' 'set JAVA_HOME to the Android Studio JBR' }
}

# 6. Docker engine
$engine = $null
if (Test-Command 'docker') {
    $engine = (& docker info --format '{{.ServerVersion}}' 2>$null)
    if ($LASTEXITCODE -ne 0) { $engine = $null }
}
if ($null -eq $engine) {
    Add-Check 'FAIL' '6 Docker engine' 'start Docker Desktop and wait until it is running'
} else {
    Add-Check 'OK' '6 Docker engine'
}

# 7. Compose v2
if ($null -eq $engine) {
    Add-Check 'FAIL' '7 Docker Compose' ("needs the Docker engine; required version >= $minCompose")
} else {
    $cv = (& docker compose version --short 2>$null)
    $parsed = $null
    if ($LASTEXITCODE -eq 0 -and $null -ne $cv -and [version]::TryParse(([string]$cv).Trim().TrimStart('v').Split('-')[0], [ref]$parsed) -and $parsed -ge $minCompose) {
        Add-Check 'OK' '7 Docker Compose'
    } else {
        Add-Check 'FAIL' '7 Docker Compose' ("version $cv found, required >= $minCompose; update Docker Desktop")
    }
}

# 8. Docker memory
if ($null -eq $engine) {
    Add-Check 'FAIL' '8 Docker memory' 'needs the Docker engine; at least 4 GB required'
} else {
    $mem = [int64](& docker info --format '{{.MemTotal}}' 2>$null)
    if ($mem -ge 4GB) { Add-Check 'OK' '8 Docker memory' } else { Add-Check 'FAIL' '8 Docker memory' ("{0:N1} GB available, at least 4 GB required (Docker Desktop settings or .wslconfig)" -f ($mem / 1GB)) }
}

# 9/10. Ports and infra/.env
$envPath = Join-Path $root 'infra\.env'
$examplePath = Join-Path $root 'infra\.env.example'
$settings = Read-DotEnv -Path $envPath
$httpPort = 8080
$dbPort = 15432
if ($settings.Contains('LOCATOR_HTTP_PORT')) { [void][int]::TryParse([string]$settings['LOCATOR_HTTP_PORT'], [ref]$httpPort) }
if ($settings.Contains('LOCATOR_DB_PORT')) { [void][int]::TryParse([string]$settings['LOCATOR_DB_PORT'], [ref]$dbPort) }

$portProblems = @()
foreach ($p in @($httpPort, $dbPort)) {
    $owner = ''
    if (-not (Test-PortUsable -Port $p -Owner ([ref]$owner))) { $portProblems += "$p used by $owner" }
}
if ($portProblems.Count -gt 0) {
    Add-Check 'FAIL' '9 Host ports' (($portProblems -join '; ') + '; change the port in infra\.env')
} else {
    Add-Check 'OK' '9 Host ports'
}

if (-not (Test-Path -LiteralPath $envPath)) {
    Add-Check 'FAIL' '10 infra/.env' 'run: Copy-Item infra\.env.example infra\.env and fill in every empty value'
} else {
    $missing = @(Get-MissingEnvNames -EnvPath $envPath -ExamplePath $examplePath)
    if ($missing.Count -gt 0) {
        Add-Check 'FAIL' '10 infra/.env' ('missing or empty: ' + ($missing -join ', '))
    } else {
        Add-Check 'OK' '10 infra/.env'
    }
}

# 11. Stack healthy (report only)
$running = @()
if ($null -ne $engine) {
    $running = @(& docker ps --filter 'label=com.docker.compose.project=locator' --format '{{.Names}}' 2>$null)
}
if ($running.Count -eq 0) {
    Add-Check 'WARN' '11 Stack healthy' 'stack not running, run up.ps1'
} else {
    $code = 0
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$httpPort/health" -UseBasicParsing -TimeoutSec 5
        $code = [int]$r.StatusCode
    } catch {
        $code = 0
    }
    if ($code -eq 200) { Add-Check 'OK' '11 Stack healthy' } else { Add-Check 'FAIL' '11 Stack healthy' "http://localhost:$httpPort/health did not answer 200; see: docker compose -f infra/docker-compose.yml logs" }
}

# 13. Phone listed
if (Test-Command 'adb') {
    $devices = @(& adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
    if ($devices.Count -gt 0) { Add-Check 'OK' '13 Phone listed' } else { Add-Check 'WARN' '13 Phone listed' 'no device in state "device"; plug in a phone with USB debugging or start an emulator' }
} else {
    Add-Check 'WARN' '13 Phone listed' 'adb not available'
}

if ($script:failed) { exit 1 }
exit 0

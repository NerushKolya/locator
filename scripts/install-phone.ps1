<#
.SYNOPSIS
  Builds the internal debug app and installs it on every connected phone and emulator.
.DESCRIPTION
  Reads LOCATOR_HTTP_PORT from infra\.env (default 8080), builds internalDebug with
  -Plocator.serverPort=<port>, then for each device in state "device":
  adb install -r, adb reverse tcp:<port> tcp:<port> (physical phones only; emulators reach the host
  as 10.0.2.2), and starts the app. Exits non-zero when no device is connected or an install fails.
.PARAMETER Serial
  Only this device (default: every device in state "device").
.PARAMETER ReverseOnly
  Only set up adb reverse (for example after re-plugging the phone); nothing is built or installed.
#>
[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$ReverseOnly
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

Initialize-AndroidEnvironment
$root = Get-RepoRoot
$adb = Get-AdbPath

$port = 8080
$settings = Read-DotEnv -Path (Join-Path $root 'infra\.env')
if ($settings.Contains('LOCATOR_HTTP_PORT')) {
    $parsed = 0
    if (-not [int]::TryParse([string]$settings['LOCATOR_HTTP_PORT'], [ref]$parsed)) {
        throw "LOCATOR_HTTP_PORT must be a port number, got '$($settings['LOCATOR_HTTP_PORT'])'."
    }
    $port = $parsed
}

$devices = @(Get-AdbDevices -Adb $adb)
if ($Serial.Length -gt 0) {
    if ($devices -notcontains $Serial) {
        throw "Device '$Serial' is not connected or not in state 'device'. Connected: $($devices -join ', ')"
    }
    $devices = @($Serial)
}
if ($devices.Count -eq 0) {
    throw 'No device connected. Start an emulator or connect a phone with USB debugging, then run adb devices.'
}

$package = 'io.github.nerushkolya.locator.internal'

if (-not $ReverseOnly) {
    $gradlew = Join-Path $root 'android\gradlew.bat'
    Push-Location (Join-Path $root 'android')
    try {
        Invoke-Native -Command $gradlew -Arguments @('--console=plain', '--quiet', "-Plocator.serverPort=$port", ':app:assembleInternalDebug')
    }
    finally {
        Pop-Location
    }
}
$apk = Join-Path $root 'android\app\build\outputs\apk\internal\debug\app-internal-debug.apk'
if ((-not $ReverseOnly) -and (-not (Test-Path -LiteralPath $apk))) {
    throw "APK not found: $apk"
}

$failed = 0
foreach ($device in $devices) {
    Write-Host "== $device"
    $isEmulator = $device.StartsWith('emulator-')
    if (-not $isEmulator) {
        $code = Invoke-NativeExitCode -Command $adb -Arguments @('-s', $device, 'reverse', "tcp:$port", "tcp:$port")
        if ($code -ne 0) { Write-Host "adb reverse failed on $device" -ForegroundColor Red; $failed++; continue }
    }
    if ($ReverseOnly) { continue }
    $code = Invoke-NativeExitCode -Command $adb -Arguments @('-s', $device, 'install', '-r', $apk)
    if ($code -ne 0) { Write-Host "adb install failed on $device" -ForegroundColor Red; $failed++; continue }
    $code = Invoke-NativeExitCode -Command $adb -Arguments @('-s', $device, 'shell', 'monkey', '-p', $package, '-c', 'android.intent.category.LAUNCHER', '1')
    if ($code -ne 0) { Write-Host "starting the app failed on $device" -ForegroundColor Red; $failed++ }
}
if ($failed -gt 0) {
    throw "$failed device(s) failed."
}
Write-Host 'Done.' -ForegroundColor Green

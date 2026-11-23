<#
.SYNOPSIS
  Streams the logcat of the internal app (its JSON log records), following app restarts.
.PARAMETER Serial
  The device to read (required when more than one device is connected).
.PARAMETER Clear
  Clear the logcat buffer first.
#>
[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$Clear
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

Initialize-AndroidEnvironment
$adb = Get-AdbPath
$package = 'io.github.nerushkolya.locator.internal'

$devices = @(Get-AdbDevices -Adb $adb)
if ($Serial.Length -eq 0) {
    if ($devices.Count -eq 0) { throw 'No device connected.' }
    if ($devices.Count -gt 1) { throw "More than one device connected ($($devices -join ', ')). Pass -Serial." }
    $Serial = $devices[0]
}
elseif ($devices -notcontains $Serial) {
    throw "Device '$Serial' is not connected or not in state 'device'."
}

if ($Clear) {
    Invoke-Native -Command $adb -Arguments @('-s', $Serial, 'logcat', '-c')
}

function Get-AppPid {
    $ErrorActionPreference = 'Continue'
    $out = (& $adb -s $Serial shell pidof $package 2>$null | Out-String).Trim()
    if ($out -match '^\d+') { return [int]($out -split '\s+')[0] }
    return 0
}

function Start-LogcatProcess {
    param([int]$ProcessId)
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $adb
    $info.Arguments = "-s $Serial logcat --pid=$ProcessId -v brief"
    $info.RedirectStandardOutput = $true
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $info
    [void]$proc.Start()
    return $proc
}

Write-Host "Reading logcat of $package on $Serial. Press Ctrl+C to stop."
$current = 0
$logcat = $null
try {
    while ($true) {
        $appPid = Get-AppPid
        if ($appPid -ne $current) {
            # logcat --pid keeps running after the process dies, so the filter is restarted on a pid change.
            if ($null -ne $logcat -and -not $logcat.HasExited) { $logcat.Kill() }
            $current = $appPid
            if ($appPid -ne 0) {
                Write-Host "-- app process $appPid" -ForegroundColor DarkGray
                $logcat = Start-LogcatProcess -ProcessId $appPid
                $reader = $logcat.StandardOutput
                $task = $reader.ReadLineAsync()
            }
        }
        if ($appPid -ne 0 -and $null -ne $task) {
            while ($task.IsCompleted) {
                $line = $task.Result
                if ($null -ne $line) { Write-Host $line }
                $task = $reader.ReadLineAsync()
            }
        }
        Start-Sleep -Milliseconds 500
    }
}
finally {
    if ($null -ne $logcat -and -not $logcat.HasExited) { $logcat.Kill() }
}

<#
.SYNOPSIS
  Runs the Android checks: unit tests, screenshot tests, lint, store-build check, internal debug build.
.DESCRIPTION
  Uses the Gradle Wrapper in android/. Order:
  1) JVM unit tests of both flavors and all modules;
  2) Roborazzi screenshot verification (or recording with -RecordScreenshots);
  3) Android Lint of the internal debug build (hard-coded text is an error);
  4) assembleStoreRelease (unsigned, R8) and verifyStoreArtifact;
  5) assembleInternalDebug.
  Exits non-zero on the first failing step. Reports: android/**/build/reports/.
.PARAMETER RecordScreenshots
  Re-record the Roborazzi baselines instead of verifying them.
#>
[CmdletBinding()]
param(
    [switch]$RecordScreenshots
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

Initialize-AndroidEnvironment
$root = Get-RepoRoot
$androidDir = Join-Path $root 'android'
$gradlew = Join-Path $androidDir 'gradlew.bat'

$screenshotTask = 'verifyRoborazziDebug'
if ($RecordScreenshots) { $screenshotTask = 'recordRoborazziDebug' }

$tasks = @(
    'test',
    $screenshotTask,
    'verifyNoHardcodedText',
    'lintInternalDebug',
    'assembleStoreRelease',
    'verifyStoreArtifact',
    'assembleInternalDebug'
)

Push-Location $androidDir
try {
    Write-Host ('== gradlew ' + ($tasks -join ' '))
    # --continue is not used: the first failure stops the run and the exit code is non-zero.
    Invoke-Native -Command $gradlew -Arguments (@('--console=plain', '--quiet') + $tasks)
}
finally {
    Pop-Location
}
Write-Host 'Android checks passed.' -ForegroundColor Green

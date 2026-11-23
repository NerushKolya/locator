<#
.SYNOPSIS
  Runs the backend tests in the containers of the Compose "test" profile.
.DESCRIPTION
  Builds the test images, starts the throw-away postgres-test database when a suite needs it,
  runs the test runner containers and writes JUnit reports to build/test-results/backend/<service>/.
  The test profile services are stopped afterwards, even on failure. The development database
  ("postgres") is never started, stopped or used. Works without infra/.env (CI): Compose gets
  throw-away values for the names it requires.
.PARAMETER Service
  core or delivery. Default: both.
.PARAMETER Unit
  Unit tests only; no database is started.
.PARAMETER AuthzMatrix
  Only the authorization matrix suites: PHPUnit group "authz-matrix" in core, Go tests matching
  "AuthzMatrix" in delivery.
#>
[CmdletBinding()]
param(
    [ValidateSet('core', 'delivery')]
    [string]$Service = '',
    [switch]$Unit,
    [switch]$AuthzMatrix
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

if ($Unit -and $AuthzMatrix) {
    throw 'Use either -Unit or -AuthzMatrix, not both.'
}

$root = Get-RepoRoot
$services = @('core', 'delivery')
if ($Service.Length -gt 0) {
    $services = @($Service)
}

$envFile = New-ThrowAwayEnvFile
$compose = (Get-ComposeArgs -EnvFile $envFile) + @('--profile', 'test')
$needsDatabase = $false
$failed = @()

try {
    foreach ($name in $services) {
        $reports = Join-Path $root "build\test-results\backend\$name"
        New-Item -ItemType Directory -Force -Path $reports | Out-Null
        $oldReport = Join-Path $reports 'junit.xml'
        if (Test-Path -LiteralPath $oldReport) {
            Remove-Item -LiteralPath $oldReport -Force
        }

        if ($name -eq 'core') {
            $build = @('build', 'core-test')
            # -e sets PHPUNIT_ARGS inside the container; the image command passes it to PHPUnit.
            $run = @('run', '--rm')
            if ($Unit) {
                $run += @('--no-deps', 'core-test', 'php', 'vendor/bin/phpunit', '--log-junit', '/reports/junit.xml', '--testsuite', 'unit')
            } else {
                $needsDatabase = $true
                $build += 'postgres-test'
                $phpunitArgs = ''
                if ($AuthzMatrix) { $phpunitArgs = '--group authz-matrix' }
                $run += @('-e', "PHPUNIT_ARGS=$phpunitArgs", 'core-test')
            }
        } else {
            # delivery has no database in this feature.
            $build = @('build', 'delivery-test')
            $goArgs = './...'
            if ($Unit) { $goArgs = '-short ./...' }
            if ($AuthzMatrix) { $goArgs = '-run AuthzMatrix ./...' }
            $run = @('run', '--rm', '--no-deps', '-e', "GO_TEST_ARGS=$goArgs", 'delivery-test')
        }

        Write-Host ''
        Write-Host "== $name tests ($(if ($Unit) { 'unit' } elseif ($AuthzMatrix) { 'authorization matrix' } else { 'all' }))"
        Invoke-Native -Command 'docker' -Arguments ($compose + $build)

        $code = Invoke-NativeExitCode -Command 'docker' -Arguments ($compose + $run)
        if ($code -ne 0) {
            Write-Host "FAIL $name tests (exit code $code)" -ForegroundColor Red
            $failed += $name
        } else {
            Write-Host "OK   $name tests" -ForegroundColor Green
        }
    }
}
finally {
    if ($needsDatabase) {
        # Only the test profile database; "postgres" and the running dev stack are left alone.
        $null = Invoke-NativeExitCode -Command 'docker' -Arguments ($compose + @('rm', '--stop', '--force', 'postgres-test'))
    }
    Remove-Item -LiteralPath $envFile -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Reports: $(Join-Path $root 'build\test-results\backend')"
if ($failed.Count -gt 0) {
    Write-Host ('Failed: ' + ($failed -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host 'All backend tests passed.' -ForegroundColor Green
exit 0

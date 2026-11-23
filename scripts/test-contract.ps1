<#
.SYNOPSIS
  Checks the API contract: Redocly lint, then oasdiff breaking changes against a base revision.
.DESCRIPTION
  1) Lints api/openapi.yaml with the rules of api/redocly.yaml.
  2) Extracts api/openapi.yaml at -Base with git show into a temporary file.
  3) Runs oasdiff "breaking --fail-on ERR" from the base to the current contract.
  Step 3 is skipped with a notice when the base revision has no contract.
.PARAMETER Base
  Git revision to compare with. Default: the merge base of HEAD and origin/main.
#>
[CmdletBinding()]
param(
    [string]$Base = ''
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\Locator.psm1') -Force

# Redocly CLI 2.82.0 and oasdiff 1.39.0, pinned by the digest of their multi-architecture index.
$redoclyImage = 'redocly/cli:2.82.0@sha256:18f9991c467e8ac74e3df035aef788a20e1aa0b44df374fef840a1230835e8d6'
$oasdiffImage = 'tufin/oasdiff:v1.39.0@sha256:004bef22058ace818b61f0eb2f05efb89fc6a3882f68bf4db0b6a86f40723fe0'

$root = Get-RepoRoot
$apiDir = Join-Path $root 'api'
$contractPath = 'api/openapi.yaml'

Write-Host '== Lint (Redocly)'
$lintArgs = @(
    'run', '--rm',
    '-e', 'REDOCLY_TELEMETRY=off',
    '-e', 'REDOCLY_SUPPRESS_UPDATE_NOTICE=true',
    '-v', "${apiDir}:/spec:ro",
    '-w', '/spec',
    $redoclyImage,
    'lint', 'openapi.yaml', '--config', 'redocly.yaml'
)
Invoke-Native -Command 'docker' -Arguments $lintArgs

Write-Host ''
Write-Host '== Breaking changes (oasdiff)'
if ($Base.Length -eq 0) {
    $ErrorActionPreference = 'Continue'
    $mergeBase = & git -C $root merge-base HEAD origin/main 2>$null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$mergeBase)) {
        throw 'Cannot find the merge base with origin/main. Run "git fetch origin main" (CI: checkout with fetch-depth: 0) or pass -Base <revision>.'
    }
    $Base = ([string]$mergeBase).Trim()
}
# A push that creates a branch reports an all-zero "before" revision: there is nothing to compare with.
if ($Base -match '^0+$') {
    Write-Host "NOTICE: base revision $Base does not exist (new branch); breaking-change check skipped."
    exit 0
}

$ErrorActionPreference = 'Continue'
& git -C $root rev-parse --verify --quiet "$Base^{commit}" | Out-Null
$baseExists = ($LASTEXITCODE -eq 0)
& git -C $root cat-file -e "${Base}:$contractPath" 2>$null
$contractExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'

if (-not $baseExists) {
    throw "Base revision '$Base' is not known to this clone. Fetch it or pass another -Base."
}
if (-not $contractExists) {
    Write-Host "NOTICE: $contractPath does not exist at $Base; breaking-change check skipped."
    exit 0
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ('locator-contract-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir | Out-Null
try {
    # Read git output as UTF-8 and write it back unchanged (Windows PowerShell 5.1 would otherwise
    # decode it with the console code page and write UTF-16).
    $previousEncoding = [Console]::OutputEncoding
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    try {
        $lines = & git -C $root show "${Base}:$contractPath"
        $code = $LASTEXITCODE
    } finally {
        [Console]::OutputEncoding = $previousEncoding
    }
    if ($code -ne 0) {
        throw "git show ${Base}:$contractPath failed with exit code $code."
    }
    $text = ([string[]]$lines -join "`n") + "`n"
    [System.IO.File]::WriteAllText((Join-Path $tempDir 'openapi.yaml'), $text, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host "Base: $Base"
    $diffArgs = @(
        'run', '--rm',
        '-v', "${tempDir}:/base:ro",
        '-v', "${apiDir}:/current:ro",
        $oasdiffImage,
        'breaking', '/base/openapi.yaml', '/current/openapi.yaml',
        '--fail-on', 'ERR',
        '--format', 'text'
    )
    $diffCode = Invoke-NativeExitCode -Command 'docker' -Arguments $diffArgs
}
finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

if ($diffCode -ne 0) {
    Write-Host "FAIL breaking changes against $Base (see the list above)." -ForegroundColor Red
    exit 1
}
Write-Host 'OK   contract lint and breaking-change check' -ForegroundColor Green
exit 0

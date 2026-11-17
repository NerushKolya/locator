# ADR-025: Developer commands — PowerShell scripts shared by the developer PC and CI

- Status: Proposed
- Date: 2026-11-17

## Context

Each test level and each routine action (start the stack, run tests, install on a phone, read logs) must be one command, and CI must call the same commands so that "green locally" equals "green in CI". The developer PC runs Windows 11 with Windows PowerShell 5.1; backend toolchains live only in Docker; Android builds use the Gradle Wrapper. GitHub-hosted Ubuntu 24.04 runners ship PowerShell 7 ([runner image contents](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)).

## Options

1. **PowerShell scripts in `scripts/`** that wrap `docker compose`, the Gradle Wrapper and `adb`. Nothing extra to install on either side.
2. **Task runner** (Task, just, Make). Nice syntax; one more tool on the PC and in CI; Make is not native on Windows.
3. **Raw commands documented in README.** Drift between documentation, the PC and CI.

## Decision

Option 1. Scripts are written for both Windows PowerShell 5.1 and PowerShell 7, take no interactive input and return a non-zero exit code on failure. Planned set (created when the walking skeleton needs them):

| Script | Purpose |
|---|---|
| `scripts/doctor.ps1` | Environment checks from [env-checklist.md](../../ops/env-checklist.md) |
| `scripts/up.ps1`, `scripts/down.ps1` | Start and stop the local stack |
| `scripts/test-backend.ps1` | Unit + integration tests of the server services in the `test` profile |
| `scripts/test-android.ps1` | JVM unit and screenshot tests, lint |
| `scripts/test-contract.ps1` | OpenAPI lint and breaking-change check |
| `scripts/e2e-backend.ps1` | Whole stack up, end-to-end scenarios |
| `scripts/install-phone.ps1` | Build the `internal` debug APK, install over `adb`, set up `adb reverse` |
| `scripts/logcat.ps1` | Filtered logcat of the app and the location engine |

## Consequences

- One entry point per action for the developer and for CI; documentation refers to scripts, not to long commands.
- Scripts must avoid features that differ between PowerShell 5.1 and 7 (covered by running them in CI on PowerShell 7 and locally on 5.1).
- Revisit if the scripts grow into a build system of their own.

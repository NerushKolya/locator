# Contract: developer commands

**Feature**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md) · ADR: [ADR-025](../../../docs/architecture/adr/ADR-025-developer-commands-powershell-scripts.md)

Rules for every script in `scripts/`:

- Runs on Windows PowerShell 5.1 and PowerShell 7 (Windows locally, Ubuntu in CI), from any current directory (paths are resolved from the script location).
- Never prompts; no `Read-Host`, no confirmation dialogs.
- Exit code `0` on success, non-zero on any failure; a failing native command (`docker`, `adb`, `gradlew`, `git`) fails the script.
- Prints short progress lines; errors name what failed and what to do.
- Touches only the repository, the Locator Compose project (`locator`) and connected devices; never system settings, global Git configuration or other containers.
- Shared helpers live in `scripts/lib/Locator.psm1`.

## `doctor.ps1`

| | |
|---|---|
| Parameters | none |
| Does | The **auto** rows of the [environment checklist](../../../docs/ops/env-checklist.md): 1 Git and local identity · 2 line endings · 3 `adb` · 4 Android SDK · 5 JDK for Gradle · 6 Docker engine · 7 Compose v2 · 8 Docker memory ≥ 4 GB · 9 host ports free or used by Locator · 10 `infra/.env` complete · 11 stack healthy · 13 phone listed |
| Output | One line per check: `OK`, `WARN` or `FAIL`, the check name, and a hint for `WARN`/`FAIL` |
| Exit code | `1` if any check is `FAIL`; `WARN` does not fail |
| Never | Starts or stops containers, installs software, changes settings. Row 11 is `WARN` ("stack not running, run up.ps1") when the stack is down |

## `up.ps1`

| | |
|---|---|
| Parameters | `-NoBuild` (skip image build) |
| Does | 1) `infra/.env` exists and every name from `infra/.env.example` is present and non-empty, else fails naming the missing names; 2) `LOCATOR_HTTP_PORT` and `LOCATOR_DB_PORT` are free or held by the Locator containers, else fails naming the port and the owning process; 3) `docker compose up -d --build --wait --wait-timeout 180` (migrations run in `core-migrate`); 4) prints the base URL and the emulator/phone addresses |
| Exit code | `0` only when every service is healthy (SC-002: within 3 min on a warm start) |

## `down.ps1`

| | |
|---|---|
| Parameters | none |
| Does | `docker compose down` for the default profile; never removes volumes (stored data survives) |

## `test-backend.ps1`

| | |
|---|---|
| Parameters | `-Service core\|delivery` (default: all existing services) · `-Unit` (unit tests only, no database) · `-AuthzMatrix` (only the authorization matrix suites of the selected services: PHPUnit group `authz-matrix` in `core`, Go tests matching `AuthzMatrix` in `delivery`) |
| Does | Builds the `test` targets, starts `postgres-test` (profile `test`, `tmpfs`) unless `-Unit`, runs migrations once, runs the test runner containers, stops the `test` profile services afterwards even on failure |
| Output | Test runner output; JUnit XML reports under `build/test-results/backend/<service>/` for CI artifacts |
| Never | Uses the development database (FR-010) |

## `test-contract.ps1`

| | |
|---|---|
| Parameters | `-Base <git ref>` (default: merge base with `origin/main`) |
| Does | 1) Redocly lint of `api/openapi.yaml` with `api/redocly.yaml`; 2) extracts the contract at `-Base` with `git show` into a temporary file; 3) oasdiff `breaking --fail-on ERR` base → current. Skips step 3 with a notice if the base has no contract |
| Output | Lint findings; list of breaking changes with their rule ids |

## `test-android.ps1`

| | |
|---|---|
| Parameters | `-RecordScreenshots` (re-record Roborazzi baselines instead of verifying) |
| Does | Gradle Wrapper in `android/`: JVM unit tests of both flavors, Roborazzi verify, Android Lint (`internalDebug`), store-build check (`assembleStoreRelease` unsigned + APK content check), `assembleInternalDebug` |
| Output | Gradle output; reports under `android/**/build/reports/` |

## `install-phone.ps1`

| | |
|---|---|
| Parameters | `-Serial <id>` (one device; default: every device in state `device`) · `-ReverseOnly` (only set up `adb reverse`, e.g. after re-plugging the phone) |
| Does | Reads `LOCATOR_HTTP_PORT` from `infra/.env` (default `8080`); builds `internalDebug` with `-Plocator.serverPort=<port>`; for each device: `adb install -r`, and for physical phones `adb reverse tcp:<port> tcp:<port>`; starts the app |
| Exit code | Non-zero when no device is connected or install fails |

## `logcat.ps1`

| | |
|---|---|
| Parameters | `-Serial <id>` · `-Clear` (clear the buffer first) |
| Does | Streams logcat filtered to the app process (`--pid` of the `internal` package), restarting the filter when the app restarts |

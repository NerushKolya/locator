# Quickstart: verify the walking skeleton

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · Commands: [contracts/developer-commands.md](contracts/developer-commands.md)

Run on the development PC in Windows PowerShell from the repository root. Each scenario names the requirement it proves.

## 0. Prerequisites

- PC prepared as in [development environment](../../docs/ops/dev-environment.md): Git, Android Studio with SDK and platform-tools, Docker Desktop running (WSL 2 engine, ≥ 4 GB memory).
- One test phone with USB debugging, or an emulator (API 29 and the latest API for SC-003).
- Local configuration:

```powershell
Copy-Item infra\.env.example infra\.env
# fill in every empty value (local passwords, APP_SECRET); keep ports unless they are taken
```

## 1. Environment check (US-4, FR-009, SC-007)

```powershell
.\scripts\doctor.ps1
```

Expected: one line per automated check; all `OK` except row 11 (`WARN`, stack not running yet) and row 13 (`WARN` if no phone is connected); exit code `0`.

Negative checks:

- Quit Docker Desktop, run `doctor.ps1` → the engine check is `FAIL` with a hint; `$LASTEXITCODE` is `1`.
- Disconnect all phones → row 13 is `WARN`, not `FAIL`.

## 2. Start the stack (US-1, FR-005, FR-006, SC-002)

```powershell
Measure-Command { .\scripts\up.ps1 } | Select-Object TotalSeconds
Invoke-RestMethod http://localhost:8080/health          # status: ok
docker compose -f infra/docker-compose.yml ps           # postgres, core, delivery healthy; core-migrate exited (0)
```

Expected: `up.ps1` exits `0` only after all services are healthy; a warm start (images present) takes under 180 s.

Edge cases:

- Remove one name (e.g. `CORE_APP_PASSWORD`) from `infra/.env` → `up.ps1` fails before starting anything and names it.
- Change `LOCATOR_HTTP_PORT` to `18080`, run `up.ps1` → `http://localhost:18080/health` answers; nothing else on the PC changes (US-1 #4). Restore `8080` afterwards.

## 3. Remote configuration (US-1 #2, FR-002, FR-003)

```powershell
$r = Invoke-WebRequest http://localhost:8080/v1/config     # no Authorization header
$r.StatusCode                                              # 200
$r.Content | ConvertFrom-Json | Select-Object version, mapStyleUrl, operatorMode
$etag = $r.Headers.ETag
(Invoke-WebRequest http://localhost:8080/v1/config -Headers @{ 'If-None-Match' = $etag } -SkipHttpErrorCheck).StatusCode  # 304 (PowerShell 7)
```

Expected: the body matches `RemoteConfig` in the contract ([data-model §1](data-model.md)); the response has an `X-Request-Id` header. In Windows PowerShell 5.1 the `304` arrives as an exception whose response status is `NotModified`.

## 4. Health reflects readiness (FR-001)

```powershell
docker compose -f infra/docker-compose.yml stop postgres
Invoke-WebRequest http://localhost:8080/health           # 503, status: down
docker compose -f infra/docker-compose.yml start postgres
Invoke-RestMethod http://localhost:8080/health           # ok again within the health-check interval
```

## 5. Data survives a restart (US-1 #3)

```powershell
.\scripts\down.ps1
.\scripts\up.ps1 -NoBuild
docker compose -f infra/docker-compose.yml exec postgres psql -U postgres -d locator -c "select version from core.doctrine_migration_versions"
```

Expected: the baseline migration is still recorded; `core-migrate` reports nothing to migrate.

## 6. Backend and contract tests (FR-003, FR-010, FR-017, FR-020)

```powershell
.\scripts\test-backend.ps1              # core and delivery: unit, integration, authorization matrix, scrubbing
.\scripts\test-backend.ps1 -Unit        # no database started
.\scripts\test-contract.ps1             # lint + breaking changes against origin/main
```

Expected: all green. While tests run, `docker compose -f infra/docker-compose.yml ps` shows `postgres-test` separately from `postgres`; the development database is untouched.

Negative check (contract): in a scratch branch remove `version` from `RemoteConfig.required` and delete the property, run `test-contract.ps1` → it fails and names the breaking change.

## 7. App on a phone and on the emulator (US-2, FR-011…FR-014, FR-019, SC-003)

```powershell
.\scripts\install-phone.ps1             # builds internalDebug, installs on every connected device, adb reverse for phones
.\scripts\logcat.ps1                    # in a second terminal: JSON records, no coordinates or tokens
```

Expected on each phone and emulator (API 29 and latest):

- Within 5 s of opening: "Server reachable" and the address used (`http://localhost:8080` on a phone, `http://10.0.2.2:8080` on the emulator).
- A map centred on Kyiv at city zoom with readable streets, Ukrainian labels and the OpenStreetMap attribution; pan to the home city for SC-004.
- No permission dialog appears; app info shows no location or notification permission requested.

Edge cases:

- `.\scripts\down.ps1`, then refresh in the app → "Server not reachable" with the address; no crash (US-2 #3). Start the stack again.
- Re-plug the phone after the stack started → `.\scripts\install-phone.ps1 -ReverseOnly` → "Server reachable" after refresh.
- Turn on airplane mode with Wi-Fi off on a phone (tiles unreachable) → status line stays, the map area shows the "map unavailable" placeholder instead of a blank screen.

## 8. Both distribution variants (US-2 #5, FR-011)

```powershell
.\scripts\test-android.ps1
```

Expected: unit, screenshot and lint checks pass; the store variant builds (release, unsigned) and the store-build check confirms the fixed server address and no `internal`-only classes.

## 9. Continuous integration (US-3, FR-015, FR-016, SC-005)

Open the feature pull request and check:

- Only jobs for changed paths ran, plus `secrets`; each job's log shows the matching `scripts/*.ps1` command.
- The pull request is mergeable only with all required checks green; total time under 15 minutes.
- A pull request that changes only `android/**` runs only `android` and `secrets`.

## 10. Memory baseline (FR-018, SC-006)

```powershell
.\scripts\up.ps1 -NoBuild
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}"
```

Expected: the sum of running containers is under 1 GB; the numbers and date are recorded next to NFR-SRV-06 in [NFR](../../docs/quality/nfr.md).

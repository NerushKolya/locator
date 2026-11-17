# Environment checklist

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [development environment](dev-environment.md) · [ADR-025](../architecture/adr/ADR-025-developer-commands-powershell-scripts.md)

Run after setting up a PC and whenever something "suddenly stopped working". `scripts/doctor.ps1` *(stage 1)* automates the rows marked **auto**; it prints one line per check (`OK`, `WARN`, `FAIL`) and exits with a non-zero code if any check fails.

| # | Check | Command | Expected | Auto |
|---|---|---|---|---|
| 1 | Git available, local identity set | `git config --local user.name` | A name is printed | auto |
| 2 | Line endings follow `.gitattributes` | `git ls-files --eol` (spot check of `.sh`, `.ps1`, `.kt`) | Text files `lf` in the index | auto |
| 3 | `adb` on `PATH` | `adb version` | Version printed | auto |
| 4 | Android SDK present | `Test-Path "$env:LOCALAPPDATA\Android\Sdk\platforms"` | `True` | auto |
| 5 | JDK for Gradle | `android\gradlew.bat -version` | JVM line shows the Android Studio JBR | auto |
| 6 | Docker engine running | `docker info --format '{{.ServerVersion}}'` | A version | auto |
| 7 | Compose v2 | `docker compose version` | `Docker Compose version v2…` or newer | auto |
| 8 | Docker memory | `docker info --format '{{.MemTotal}}'` | ≥ 4 GB | auto |
| 9 | Host ports free or used by Locator | `Get-NetTCPConnection -LocalPort 8080,15432 -State Listen` | Nothing, or the Locator containers | auto |
| 10 | `infra/.env` exists and has every name from `.env.example` | Script compares the two files | No missing names | auto |
| 11 | Stack healthy | `.\scripts\up.ps1`, then `Invoke-WebRequest http://localhost:8080/health` | `200` | auto |
| 12 | Emulator reaches the backend | In the emulator browser open `http://10.0.2.2:8080/health` | `ok` | manual |
| 13 | Phone listed | `adb devices` | Each phone shows `device` | auto (warn if none) |
| 14 | Phone reaches the backend | `adb reverse tcp:8080 tcp:8080`, open `http://localhost:8080/health` in the phone browser | `ok` | manual |
| 15 | Android build | `android\gradlew.bat -p android assembleInternalDebug` | `BUILD SUCCESSFUL` | manual (slow) |
| 16 | Tailscale Funnel (field tests only) | `tailscale funnel status` | The Locator port is published | manual |

The script never changes system settings, never installs software and never touches anything outside the repository and the Locator containers.

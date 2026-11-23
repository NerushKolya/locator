# Development environment

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [environment checklist](env-checklist.md) · [architecture overview §8](../architecture/overview.md) · [ADR-024](../architecture/adr/ADR-024-test-database-compose-profile.md) · [ADR-025](../architecture/adr/ADR-025-developer-commands-powershell-scripts.md)

Set-up of a Windows 11 PC from zero. All commands are for **Windows PowerShell** unless stated otherwise. Steps marked *(stage 1)* become available with the walking skeleton.

## 1. Layout of the environment

| Part | Where it runs |
|---|---|
| Android app | Natively on Windows: Android Studio, its bundled JDK (JBR), Gradle Wrapper, emulator, phones over USB |
| Backend services and PostgreSQL + PostGIS | Only in Docker (Docker Desktop with the WSL 2 engine). No PHP, Go, Node or database on the host |
| Dependency folders (`vendor`, Go module cache, `node_modules`) | Docker named volumes, not bind mounts: on a Windows drive a bind mount was measured about 26× slower for many small files |
| Repository | A folder on the Windows file system, for example `D:\work\locator` |

## 2. Install

### 2.1. Git

1. Install Git for Windows from <https://git-scm.com/download/win> with default options.
2. Clone and set the identity **for this repository only** (do not change the global configuration if the PC is shared between accounts):

```powershell
git clone https://github.com/NerushKolya/locator.git D:\work\locator
cd D:\work\locator
git config --local user.name  "<Your Name>"
git config --local user.email "<your-email>"
```

### 2.2. Android Studio and SDK

1. Install the current stable Android Studio from <https://developer.android.com/studio>. It bundles the JDK (JBR); no separate JDK is needed.
2. In **SDK Manager** install: the SDK Platform matching `compileSdk` of the project, Android SDK Platform-Tools, Android Emulator, and system images for API 29, API 30 and the latest API (Google APIs or Google Play images).
3. Add platform-tools to the user `PATH` and open a **new** terminal afterwards:

```powershell
$pt = "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
$p  = [Environment]::GetEnvironmentVariable("Path", "User")
if ($p -notlike "*$pt*") { [Environment]::SetEnvironmentVariable("Path", "$p;$pt", "User") }
```

4. In **Device Manager** create three virtual devices: API 29, API 30 and the latest API.

### 2.3. Phones

1. Enable developer options: *Settings → About phone →* tap *Build number* seven times.
2. In developer options turn on **USB debugging**.
3. Connect by USB, accept the RSA fingerprint prompt on the phone, then:

```powershell
adb devices      # the phone is listed as "device", not "unauthorized"
```

### 2.4. Docker Desktop

1. Install Docker Desktop from <https://docs.docker.com/desktop/setup/install/windows-install/> with the **WSL 2** engine. Integration with other WSL distributions is **not** needed and can stay off.
2. Check:

```powershell
docker run --rm hello-world
docker compose version
```

3. In *Settings → Resources* make sure at least 4 GB of memory is available to Docker.

### 2.5. Optional tools

| Tool | Needed for | Install |
|---|---|---|
| Tailscale | Outdoor tests against the local stack (stage 2) | <https://tailscale.com/download/windows> |
| Maestro CLI | End-to-end flows on phones (from stage 5) | Runs on the JVM; installation instructions at <https://docs.maestro.dev/> |

## 3. Run the project *(stage 1)*

```powershell
cd D:\work\locator
Copy-Item infra\.env.example infra\.env    # then fill in local values
.\scripts\doctor.ps1                        # checks from env-checklist.md
.\scripts\up.ps1                            # starts the local stack and waits for /health
```

`up.ps1` builds the images and starts the Compose project `locator`: `postgres` (PostgreSQL + PostGIS), `core-migrate` (applies pending migrations once, then exits), `core` (Symfony on FrankenPHP, the only service with an HTTP port on the host) and `delivery` (Go, no host port yet). It returns only when every service reports healthy, or fails after 180 s. `.\scripts\down.ps1` stops the stack.

Open the `android/` folder in Android Studio and run the `internalDebug` variant.

Install on every connected phone and emulator (builds `internalDebug` for the port in `infra/.env`, installs, sets `adb reverse` for phones, starts the app), and read the app's log records:

```powershell
.\scripts\install-phone.ps1      # -Serial <id> for one device, -ReverseOnly after re-plugging a phone
.\scripts\logcat.ps1             # JSON records of the app; -Clear empties the buffer first
.\scripts\test-android.ps1       # unit and screenshot tests, lint, store-build check
```

The scripts set `JAVA_HOME` (Android Studio runtime) and `ANDROID_HOME` for their own process when the variables are not set.

### 3.1. Ports and `infra/.env`

`infra/.env` is created from `infra/.env.example` (the example lists every name; secret values are empty and are filled in locally; the real file is never committed). `up.ps1` stops before starting anything if a name is missing or empty and names it.

| Name | Meaning |
|---|---|
| `COMPOSE_PROJECT_NAME` | Always `locator` |
| `LOCATOR_HTTP_PORT`, `LOCATOR_DB_PORT` | Host ports of `core` and `postgres` |
| `LOCATOR_PUBLIC_BASE_URL` | Base of the legal page links in `/v1/config` and of problem `type` URIs; its port follows `LOCATOR_HTTP_PORT` |
| `POSTGRES_PASSWORD`, `CORE_MIGRATOR_PASSWORD`, `CORE_APP_PASSWORD`, `DELIVERY_APP_PASSWORD` | Local passwords of the database superuser and the three service roles; any non-empty values |
| `APP_SECRET` | Symfony secret; any long random string |
| `LOG_LEVEL` | `debug`, `info`, `warning` or `error` for `core` and `delivery` |
| `OPERATOR_MODE` | `off` (default) or `on` |

The backend tests do not use `infra/.env` or the development database: `test-backend.ps1` runs the containers of the Compose `test` profile with a throw-away `postgres-test` (data in memory) and fixed test values, so it works the same on a fresh clone and in CI.

The local stack publishes one HTTP port on the host, `8080` by default (`LOCATOR_HTTP_PORT`), and the database on `15432` (`LOCATOR_DB_PORT`) for desktop database tools. Both are bound to `127.0.0.1` only. If a port is taken by another program, change **only** the Locator port in `infra/.env`; when you change the HTTP port, change the port in `LOCATOR_PUBLIC_BASE_URL` too. `up.ps1` and `doctor.ps1` check both ports first and name the owning process when one is taken (a port published by the Locator containers themselves is fine):

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

`.\scripts\down.ps1` stops the stack and never removes the data volume.

### 3.2. Reaching the local backend

| Client | Base URL |
|---|---|
| Emulator | `http://10.0.2.2:8080` (the emulator's alias for the host) |
| Phone over USB | Run `adb reverse tcp:8080 tcp:8080`, then `http://localhost:8080` |
| Phone outdoors (stage 2) | Tailscale Funnel: `tailscale funnel 8080` publishes `https://<pc-name>.<tailnet>.ts.net` |

If the host port was changed, use the same number in `adb reverse` and in the app's server setting (`internal` flavor only).

## 4. Daily commands *(stage 1, see [ADR-025](../architecture/adr/ADR-025-developer-commands-powershell-scripts.md))*

| Action | Command |
|---|---|
| Start / stop the stack | `.\scripts\up.ps1` / `.\scripts\down.ps1` |
| Backend tests (unit + integration + authorization matrix) | `.\scripts\test-backend.ps1` (`-Service core\|delivery`, `-Unit` without a database, `-AuthzMatrix`); JUnit reports in `build/test-results/backend/` |
| Contract checks (Redocly lint + oasdiff breaking changes against the merge base with `origin/main`) | `.\scripts\test-contract.ps1` (`-Base <revision>`) |
| Backend end-to-end | `.\scripts\e2e-backend.ps1` |
| Android unit, screenshot tests, lint | `.\scripts\test-android.ps1` |
| Build and install on the connected phone | `.\scripts\install-phone.ps1` |
| Read app and engine logs | `.\scripts\logcat.ps1` |
| Service logs | `docker compose -f infra/docker-compose.yml logs -f core` |

## 5. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `adb` or `docker` not found right after installation | The terminal was started before `PATH` changed; open a new terminal |
| `docker` cannot connect to the engine | Docker Desktop is not running; start it and wait until the engine is ready |
| Phone shows `unauthorized` | Re-plug the cable and accept the fingerprint prompt; revoke USB debugging authorizations if needed |
| Emulator cannot reach the backend | Use `10.0.2.2`, not `localhost`; check the host port |
| Backend tests are slow on first run | Images and dependency volumes are being created; later runs reuse them |

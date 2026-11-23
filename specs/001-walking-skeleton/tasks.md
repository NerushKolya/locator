---

description: "Task list for the walking skeleton"
---

# Tasks: Walking skeleton

**Input**: Design documents from `specs/001-walking-skeleton/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required. The constitution (principle VII) asks for a failing test before each implementation task; FR-003 and FR-020 require contract validation and scrubbing tests. Test tasks come first in each group and must fail before the implementation task that follows them. Tooling tasks (scripts, Compose services, Gradle setup, CI) carry an **Acceptance** note naming a quickstart scenario: run it before the change (it fails), make the change, run it again (it passes), and mention both outcomes in the commit message ([plan: test-first for tooling tasks](plan.md#test-first-for-tooling-tasks)).

**Organization**: Tasks are grouped by user story. 👤 marks tasks that need the owner.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story from spec.md (US1…US4)
- Paths are relative to the repository root

## Implementation groups

Work is done one group per session; each group ends with green tests and a commit.

| Group | Tasks | Result |
|---|---|---|
| Infrastructure | T001–T010, T056–T057 | Database image, Compose base, `up`/`down`, `doctor` |
| Backend | T011–T033 | `core`, `delivery`, backend and contract test commands |
| Android | T034–T051 | App on phone and emulator, Android test, install and log commands |
| CI | T052–T055 | `ci.yml` (built up in steps, see the CI phase), Dependabot, required checks |
| Finish and demo | T058–T061 | Quickstart run, demo, memory baseline, map decision |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository-level preparation shared by every code base.

- [x] T001 Set the feature status to `In progress` in `specs/001-walking-skeleton/spec.md` and `specs/README.md`; extend `.gitignore` with `backend/core/var/`, `android/.kotlin/`, `build/test-results/` and `.gitattributes` with `gradlew text eol=lf` and `*.sh text eol=lf`
- [x] T002 [P] Create the shared PowerShell module `scripts/lib/Locator.psm1` per [developer-commands contract](contracts/developer-commands.md) "Rules": `Get-RepoRoot`, `Test-IsWindowsHost` (works without `$IsWindows`, absent in PowerShell 5.1), `Read-DotEnv` (returns ordered name/value pairs, ignores comments and blank lines), `Invoke-Native` (runs a command, throws with the command line when `$LASTEXITCODE` is non-zero), `Write-CheckLine` (`OK`/`WARN`/`FAIL` + name + hint), `Get-ComposeArgs` (`-f infra/docker-compose.yml --env-file infra/.env -p locator`); no PowerShell 7-only syntax (`??`, `?:`, `&&`, `||`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The contract change and the database image that every story builds on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Apply [contracts/openapi-changes.md](contracts/openapi-changes.md) §1–§2 to `api/openapi.yaml`: `GET /v1/config` gets `security: []` and loses the `'401'` response, description as in the contract; `GET /health` summary "Readiness probe" and the new description. Update `docs/api/README.md` §3 row "Access token" per contract §3
- [x] T004 [P] Add `api/redocly.yaml` (`extends: [recommended]`), run Redocly CLI from its official Docker image on `api/openapi.yaml`, fix real findings in the contract and list every switched-off or downgraded rule with a one-line reason in `api/redocly.yaml`
- [x] T005 [P] Create `infra/postgres/Dockerfile` per ADR-020: `FROM postgres:18-bookworm@sha256:<digest>` (digest looked up on Docker Hub in the task) and `postgresql-18-postgis-3` from the PostgreSQL apt repository; create `infra/postgres/initdb/10-schemas-roles.sh` per [data-model §4](data-model.md#4-database-baseline): `CREATE EXTENSION postgis`; roles `core_migrator`, `core_app`, `delivery_app` with passwords from `CORE_MIGRATOR_PASSWORD`, `CORE_APP_PASSWORD`, `DELIVERY_APP_PASSWORD`; schema `core` owned by `core_migrator` with `USAGE` for `core_app`; schema `delivery` owned by `delivery_app`; default privileges by `core_migrator` in `core`: "`SELECT, INSERT, UPDATE, DELETE` on future tables and `USAGE, SELECT` on future sequences for `core_app`"; script fails on the first error (`set -euo pipefail`, `ON_ERROR_STOP=1`); confirm FR-004 for the production architecture once with `docker buildx build --platform linux/arm64 infra/postgres` (emulated build is enough here; native `arm64` builds come with `images.yml` in stage 8)
- [x] T006 Create `infra/.env.example` with exactly the 11 names of [data-model §3](data-model.md#3-local-configuration-infraenv) and their example values (secrets empty), with a comment next to `LOCATOR_PUBLIC_BASE_URL` to change its port together with `LOCATOR_HTTP_PORT`; and `infra/docker-compose.yml` with: service `postgres` (build `infra/postgres`, `POSTGRES_DB: locator`, volume `pgdata`, port `"127.0.0.1:${LOCATOR_DB_PORT:-15432}:5432"`, health check `pg_isready -U postgres -d locator`); service `postgres-test` in profile `test` (same build, data on `tmpfs`, no host port, fixed throw-away values defined in the Compose file only for all four variables the init script needs: `POSTGRES_PASSWORD`, `CORE_MIGRATOR_PASSWORD`, `CORE_APP_PASSWORD`, `DELIVERY_APP_PASSWORD`; `core-test` and `delivery-test` reuse the same values)
- [x] T007 Verify the database baseline by hand and record the commands in the task's commit message: build and start `postgres`, then `psql` shows the `postgis` extension, schemas `core` and `delivery` with their owners, the three roles; `core_app` cannot `CREATE TABLE core.x`; restart keeps the data

**Checkpoint**: Contract and database ready; user story work can begin.

---

## Phase 3: User Story 1 - Start the server stack with one command (Priority: P1) 🎯 MVP

**Goal**: A fresh clone with `infra/.env` reaches a healthy stack (database, migrations, `core`, `delivery`) with one command; `GET /v1/config` answers per the contract.

**Independent Test**: Quickstart §2–§6: `up.ps1` exits `0` only when all services are healthy; `Invoke-RestMethod http://localhost:8080/v1/config` returns a contract-valid document without credentials; `down.ps1` + `up.ps1 -NoBuild` keeps the migration record; `test-backend.ps1` and `test-contract.ps1` are green.

### Stack commands

- [x] T008 [US1] Create `scripts/up.ps1` per [developer-commands contract](contracts/developer-commands.md#upps1): validate `infra/.env` against `infra/.env.example` ("every name … present and non-empty", fail naming the missing names before starting anything); check `LOCATOR_HTTP_PORT` and `LOCATOR_DB_PORT` (free, or published by a container of Compose project `locator`; otherwise fail naming port and owning process via `Get-NetTCPConnection`/`Get-Process` on Windows, `ss` elsewhere); run `docker compose up -d --build --wait --wait-timeout 180` (`--build` omitted with `-NoBuild`); print `http://localhost:<port>`, emulator `http://10.0.2.2:<port>` and the `adb reverse` hint. **Acceptance**: quickstart §2 (with only `postgres` defined it must reach "healthy"; the missing-name edge case must fail before any container starts)
- [x] T009 [P] [US1] Create `scripts/down.ps1`: `docker compose down` for the default profile, never `-v`. **Acceptance**: quickstart §5 (data survives `down` + `up -NoBuild`)
- [x] T010 [US1] Check the edge cases of `up.ps1` by hand per quickstart §2: a removed name in `infra/.env` stops before any container starts; `LOCATOR_HTTP_PORT=18080` works on the new port (once `core` exists, re-check in T033)

### Core service: skeleton

- [ ] T011 [US1] Create `backend/core/Dockerfile` target `base`: official FrankenPHP image for PHP 8.5 (Debian variant, pinned by digest; exact tag confirmed on the official FrankenPHP site/repository in the task), PHP extensions `pdo_pgsql`, `intl`, `opcache`, `zip`, Composer binary copied from the official Composer image; `SERVER_NAME=:8080`
- [ ] T012 [US1] Scaffold the Symfony application in `backend/core/` by running Composer inside the `base` image: latest stable Symfony 8.x skeleton (8.1 or 8.2 if released, [research R-01](research.md#r-01-server-runtimes-and-versions)), packages `symfony/runtime`, `symfony/framework-bundle`, `symfony/monolog-bundle`, `symfony/uid`, `doctrine/doctrine-bundle`, `doctrine/doctrine-migrations-bundle`; dev: PHPUnit (current major), `symfony/browser-kit`; namespace `App\`; code under `src/Platform/`; remove skeleton files that are not used; commit `composer.lock`
- [ ] T013 [US1] Complete `backend/core/Dockerfile` with targets `test` (dev dependencies, runs migrations against `postgres-test` then PHPUnit with JUnit output to `/reports`) and `runtime` (no dev dependencies, no `.env` file with values baked into the image, opcache on); add `backend/core/Caddyfile` serving only the PHP app on `:8080` ([research R-06](research.md#r-06-edge-proxy-scope-in-this-feature)); configure two DBAL connections in `backend/core/config/packages/doctrine.yaml`: `default` as `core_app`, `migrations` as `core_migrator`, migrations metadata table `core.doctrine_migration_versions`
- [ ] T014 [US1] Add to `infra/docker-compose.yml`: `core-migrate` (image of `backend/core` target `runtime`, command `php bin/console doctrine:migrations:migrate --no-interaction --allow-no-migration` on the `migrations` connection, `depends_on: postgres: condition: service_healthy`, `restart: "no"`); `core` (same image, port `"127.0.0.1:${LOCATOR_HTTP_PORT:-8080}:8080"`, `depends_on: core-migrate: condition: service_completed_successfully`, health check `php -r 'exit(@file_get_contents("http://127.0.0.1:8080/health") === false ? 1 : 0);'`, env from `infra/.env`); `core-test` in profile `test` (target `test`, `depends_on: postgres-test` healthy, read-only bind mount `../api:/contract:ro`, env `OPENAPI_CONTRACT_PATH=/contract/openapi.yaml`, volume or bind for `/reports`). **Acceptance**: `up.ps1` reaches healthy with `core-migrate` exited `0` and `core` healthy (quickstart §2; `/health` answers once T026 exists — until then a placeholder route returning `{"status":"ok"}` is allowed and removed in T026)

### Core service: logs, request id, errors

- [ ] T015 [P] [US1] Write failing `backend/core/tests/Unit/Platform/Logging/ScrubbingProcessorTest.php` covering all 9 vectors of [log-records contract §4](contracts/log-records.md#4-test-vectors) and the record fields of §1 (`ts`, `level`, `service`, `msg`, `requestId`)
- [ ] T016 [US1] Implement `backend/core/src/Platform/Logging/ScrubbingProcessor.php` (layer 1 by key, layer 2 by pattern, recursive, per contract §2) and configure Monolog in `backend/core/config/packages/monolog.yaml`: JSON lines to stdout, field names per contract §1, `service` = `core` (`core-migrate` for the migration command), level from `LOG_LEVEL`; make T015 pass
- [ ] T017 [P] [US1] Write failing `backend/core/tests/Api/RequestIdAndErrorsTest.php`: a valid `X-Request-Id` (`^[A-Za-z0-9-]{8,64}$`) is echoed, a malformed or missing one is replaced by a generated UUID v7 and returned in the header; the id appears in the request log record; an unknown path returns `404` `application/problem+json` with `code: not_found` and `requestId`; an unhandled exception returns `500` with `code: internal_error` and no details
- [ ] T018 [US1] Implement `backend/core/src/Platform/Http/RequestIdSubscriber.php` (request attribute + Monolog context + response header) and `backend/core/src/Platform/Http/ProblemJsonExceptionListener.php`; make T017 pass

### Core service: contract validation and remote configuration

- [ ] T019 [US1] Select the OpenAPI 3.1 response validator per [research R-02](research.md#r-02-validating-core-responses-against-an-openapi-31-contract-fr-003) (Gesso → `league/openapi-psr7-validator` → JSON Schema 2020-12 fallback): the chosen one must load the whole contract from `OPENAPI_CONTRACT_PATH` and reject a deliberately wrong `/health` body (`{"status":"fine"}`); wrap it in `backend/core/tests/Support/AssertsOpenApiResponse.php` (trait for `WebTestCase`) with a self-test in `backend/core/tests/Support/AssertsOpenApiResponseTest.php`; record the choice and the reason in `docs/quality/testing-strategy.md` §3
- [ ] T020 [P] [US1] Write failing `backend/core/tests/Unit/Platform/RemoteConfig/BuiltInRemoteConfigTest.php`: every default of [data-model §1 and §1.1](data-model.md#1-remote-configuration-get-v1config) with its exact value (`version` `1`, `mapStyleUrl` `https://tiles.openfreemap.org/styles/liberty` or the URL confirmed on openfreemap.org, limits `20/10/20/100/1000/72/900/2000/15`, the 13 engine keys); `legal.termsUrl` = `${LOCATOR_PUBLIC_BASE_URL}/terms`, `legal.privacyUrl` = `${LOCATOR_PUBLIC_BASE_URL}/privacy`, `legal.termsVersion` `"0"`; `operatorMode` `true` only for `OPERATOR_MODE=on`
- [ ] T021 [P] [US1] Write failing `backend/core/tests/Api/RemoteConfigApiTest.php` (uses T019 trait): `GET /v1/config` without credentials → `200`, body valid against `RemoteConfig`; "`ETag`: strong, quoted hash of the serialized response body", stable across requests; `If-None-Match` equal to it → "`304 Not Modified` without a body"; other value → `200`; `Cache-Control: no-cache`
- [ ] T022 [US1] Implement `backend/core/src/Platform/RemoteConfig/BuiltInRemoteConfig.php` and `backend/core/src/Platform/RemoteConfig/RemoteConfigController.php` (route `GET /v1/config`, ETag and `304` handling, `config.served` debug record); make T020 and T021 pass

### Core service: readiness and migrations

- [ ] T023 [P] [US1] Write failing `backend/core/tests/Unit/Platform/Health/ReadinessProbeTest.php` with fakes: database error or timeout → `down`; database ok and an unexecuted migration → `down`; database ok and no pending migration → `ok`
- [ ] T024 [P] [US1] Write failing `backend/core/tests/Integration/Platform/Health/HealthIntegrationTest.php` against `postgres-test`: after migrations `GET /health` → `200 {"status":"ok"}`; with an unreachable database DSN → `503 {"status":"down"}`; both bodies valid against `Health` (T019 trait) and contain no other field
- [ ] T025 [P] [US1] Write failing `backend/core/tests/Integration/Database/BaselineTest.php` against `postgres-test`: extension `postgis` exists; `core_app` can `SELECT` from `core.doctrine_migration_versions`; `core_app` cannot create a table in schema `core`
- [ ] T026 [US1] Implement `backend/core/src/Platform/Health/ReadinessProbe.php` (`SELECT 1` with ≈ 1 s timeout; available vs executed migrations from Doctrine Migrations, metadata read through the `default` connection) and `backend/core/src/Platform/Health/HealthController.php` (route `GET /health`, `200 ok` / `503 down`, logged at `debug` only); add the baseline migration `backend/core/migrations/Version20261123000000.php` that aborts with "PostGIS extension missing" when `pg_extension` has no `postgis` row and creates no tables; make T023–T025 pass

### Core service: authorization matrix

- [ ] T027 [US1] Create `backend/core/tests/AuthzMatrix/AuthorizationMatrixTest.php` as a data table (columns: method, path, caller, expected status) with rows `GET /health` anonymous → `200` and `GET /v1/config` anonymous → `200`, in PHPUnit group `authz-matrix`; document the row format in a class doc comment so later features add rows

### Delivery service

- [ ] T028 [P] [US1] Scaffold `backend/delivery/`: `go.mod` (module `github.com/NerushKolya/locator/backend/delivery`, Go 1.26), `backend/delivery/Dockerfile` (official `golang:1.26` image pinned by digest; target `test` running `go test ./... ` with JUnit output to `/reports`; target `runtime`: static binary `CGO_ENABLED=0` on a minimal non-root base image, pinned by digest); add `delivery` (no host port, health check `["CMD", "/delivery", "-healthcheck"]`) and `delivery-test` (profile `test`) to `infra/docker-compose.yml`. **Acceptance**: after T031, `up.ps1` waits for `delivery` healthy (quickstart §2)
- [ ] T029 [P] [US1] Write failing `backend/delivery/internal/logging/scrub_test.go` with all 9 vectors of [log-records contract §4](contracts/log-records.md#4-test-vectors), then implement `backend/delivery/internal/logging/` (a `slog.Handler` wrapping `slog.JSONHandler`, field names per contract §1, `service` = `delivery`, level from `LOG_LEVEL`)
- [ ] T030 [P] [US1] Write failing `backend/delivery/internal/requestid/requestid_test.go` (same rules as T017), then implement the middleware in `backend/delivery/internal/requestid/`
- [ ] T031 [US1] Write failing `backend/delivery/internal/health/health_test.go` (`GET /health` → `200 {"status":"ok"}`, no other field) and `backend/delivery/cmd/delivery/main_test.go` (`-healthcheck` exits `0` against a running server, `1` when nothing listens); implement `backend/delivery/internal/health/` and `backend/delivery/cmd/delivery/main.go` (listen address from `DELIVERY_HTTP_ADDR`, default `:8081`; graceful shutdown on SIGTERM/SIGINT within 10 s; request log records per contract); add the `delivery` authorization matrix `backend/delivery/internal/health/authz_matrix_test.go` as a table-driven test named `TestAuthzMatrix` with the row `GET /health` anonymous → `200`, documented so later features add rows

### Backend test commands

- [ ] T032 [US1] Create `scripts/test-backend.ps1` per [developer-commands contract](contracts/developer-commands.md#test-backendps1): `-Service core|delivery` (default both), `-Unit` (no `postgres-test`; PHPUnit `--testsuite unit`, `go test -short`), `-AuthzMatrix` (both services by default: PHPUnit `--group authz-matrix` in `core`, `go test -run AuthzMatrix ./...` in `delivery`); `docker compose --profile test` build and run of the runner containers; reports copied to `build/test-results/backend/<service>/`; `test` profile services stopped in a `finally` block; non-zero exit on any failure. Never touches `postgres`. **Acceptance**: quickstart §6 (green run; a deliberately failing assertion makes the script exit non-zero; `postgres` data unchanged)
- [ ] T033 [P] [US1] Create `scripts/test-contract.ps1` per [developer-commands contract](contracts/developer-commands.md#test-contractps1): Redocly lint (image pinned by digest); base contract via `git show <base>:api/openapi.yaml` into a temporary file (`-Base` default: `git merge-base HEAD origin/main`; skip with a notice when the base has no contract); oasdiff `breaking --fail-on ERR` with the `tufin/oasdiff` tag that parses OpenAPI 3.1 ([research R-09](research.md#r-09-contract-checks-fr-017)); prove in the task that a copy with `version` removed from `RemoteConfig` fails and that T003 passes (record any waiver per R-09). **Acceptance**: quickstart §6 contract part, including the negative check. Then re-run quickstart §2–§6 and the T010 port check
- [ ] T034 [US1] Update `docs/architecture/backend.md` §10 (one `/health` per service, readiness semantics of [research R-04](research.md#r-04-health-semantics-fr-001-clarification-of-2026-11-23)), `docs/architecture/overview.md` §6 (remote configuration document in `Platform`, [research R-07](research.md#r-07-remote-configuration-in-the-core-fr-002)) and `docs/ops/dev-environment.md` §3 (`.env` names, ports bound to `127.0.0.1`, `up.ps1`/`down.ps1`)

**Checkpoint**: User Story 1 is fully functional: quickstart §2–§6 pass.

---

## Phase 4: User Story 2 - See the app talk to the server on a phone and on the emulator (Priority: P1)

**Goal**: One command installs the `internal` debug build on phones and emulators; the app shows the server status with its address and a map drawn with the style URL from `/v1/config`; both flavors build.

**Independent Test**: Quickstart §7–§8 with the stack running: "Server reachable" within 5 s on a phone (via `adb reverse`) and on the emulator (via `10.0.2.2`); map of Kyiv with attribution; stopped stack shows "Server not reachable" with the address; `test-android.ps1` green including the store-build check. JVM tests use fakes and do not need US1.

### Project and modules

- [ ] T035 [US2] Create the Gradle project in `android/`: Gradle Wrapper (latest stable Gradle supported by the chosen AGP; wrapper generated with an official Gradle Docker image or Android Studio, `gradle-wrapper.jar` committed), `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml` with the latest stable AGP 9.x, Compose BOM, Hilt, KSP, Ktor, kotlinx.serialization, kotlinx-coroutines, Turbine, Robolectric, Roborazzi, JUnit (versions checked on official release pages in the task, [research R-10](research.md#r-10-android-build-and-modules-fr-011fr-014-fr-019)); convention plugins in `android/build-logic/`: `locator.android.application`, `locator.android.library`, `locator.android.feature`, `locator.jvm.library`, `locator.android.compose`, `locator.hilt` (compileSdk 36, minSdk 29, targetSdk 36, Java/Kotlin toolchain, Lint `HardcodedText` as error). **Acceptance**: `gradlew -p android projects` lists the planned modules and `gradlew -p android help` succeeds (both fail before the task)
- [ ] T036 [US2] Create `android/app/`: namespace and `applicationId` `io.github.nerushkolya.locator`; flavor dimension `distribution` with `internal` (`applicationIdSuffix ".internal"`) and `store`; single activity + `@HiltAndroidApp` application; manifest with only `INTERNET` and `ACCESS_NETWORK_STATE` (FR-019), `allowBackup="false"`, data-extraction rules excluding everything; `android/app/src/debug/res/xml/network_security_config.xml` allowing clear text only for `localhost`, `127.0.0.1`, `10.0.2.2`; `app_name` "Loomi" in `values/` and `values-uk/`; adaptive launcher icon converted from `docs/design/icon/ic_launcher_{foreground,background,monochrome}.svg` to vector drawables. **Acceptance**: `assembleInternalDebug` and `assembleStoreDebug` succeed and the app starts on the emulator with an empty screen (quickstart §8, build part)
- [ ] T037 [P] [US2] Create `android/core/designsystem/` from `docs/design/theme/Color.kt`, `Theme.kt`, `Type.kt`, package `io.github.nerushkolya.locator.core.designsystem.theme`
- [ ] T038 [P] [US2] Create `android/core/testing/`: `MainDispatcherRule`, test dispatchers, a fake for the health and configuration API used by `core:data` and `feature:map` tests
- [ ] T039 [P] [US2] Create `android/core/model/` (pure Kotlin): `ServerAddress(baseUrl, configurable)`, `ServerStatus` (`Reachable`, `NotReady(status)`, `Unreachable(baseUrl, reason)` with `reason` one of timeout, connection refused, unexpected response — "never a raw exception text"), `RemoteConfig(version, mapStyleUrl)` per [data-model §5](data-model.md#5-app-state-featuremap)

### Logging

- [ ] T040 [US2] Write failing JVM tests in `android/core/common/src/test/` with all 9 vectors of [log-records contract §4](contracts/log-records.md#4-test-vectors); implement `android/core/common/` (pure Kotlin): `Logger`, scrubbing per contract §2, `LogSink` interface, record format per §1 with `service` = `app`; add `AndroidLogSink` (tag `Locator`, drops `debug` in release builds) in `android/app/`

### Network, address, data

- [ ] T041 [US2] Write failing tests in `android/core/network/src/test/`: DTOs decode a contract-shaped `RemoteConfig` and `Health`, ignore unknown fields, fail on a missing required field; with Ktor `MockEngine`: `200 ok` → reachable, `503 down` → not ready, connection error → unreachable, timeouts "connect 3 s, request 5 s"; implement `android/core/network/` (Ktor OkHttp engine, kotlinx.serialization, `HealthApi`, `ConfigApi`, base URL from `ServerAddress`)
- [ ] T042 [US2] Write failing tests for the server address ([research R-11](research.md#r-11-server-address-per-flavor-and-device-fr-011-fr-012-us-2)): `internal` — emulator build properties → `http://10.0.2.2:<port>`, phone → `http://localhost:<port>`, port from Gradle property `locator.serverPort` (default `8080`); `store` — fixed `https://locator.example.org`, `configurable == false`; implement `ServerAddressProvider` in `android/app/src/internal/` (package `io.github.nerushkolya.locator.internal`) and `android/app/src/store/`, bound with Hilt
- [ ] T043 [US2] Write failing tests in `android/core/data/src/test/` using the T038 fake; implement `ServerStatusRepository` and `RemoteConfigRepository` in `android/core/data/`

### Map screen

- [ ] T044 [US2] Write failing `MapViewModel` tests (Turbine) in `android/feature/map/src/test/`: initial state loading → `Reachable` + `MapSource.Ready(styleUrl)`; server unreachable → `Unreachable(baseUrl, reason)` + `MapSource.Unavailable`; `503` → `NotReady`; configuration error → `MapSource.Unavailable`; `Refresh` re-reads both; implement `MapUiState` and `MapViewModel` in `android/feature/map/`
- [ ] T045 [US2] Map prototype, time-boxed to 2 h ([research R-12](research.md#r-12-map-integration-fr-013-adr-018-open-point)): MapLibre Compose with the style URL at runtime, attribution visible, style-load failure reported, APK size delta measured; if any criterion fails, MapLibre Native through `AndroidView`. Record the outcome in `docs/architecture/android.md` §7 and in the "Consequences" of `docs/architecture/adr/ADR-018-maps-maplibre-openfreemap.md` (map quality is judged in T060)
- [ ] T046 [US2] Implement `MapScreen` in `android/feature/map/`: status line with text and icon (never colour alone), the address used, refresh action ≥ 48 dp, map slot with `DefaultCamera` "latitude 50.4501, longitude 30.5234, zoom 11", "map unavailable" placeholder; all strings in `values/` (English) and `values-uk/` (Ukrainian); Roborazzi screenshot tests in `android/feature/map/src/test/` for reachable, not reachable and map unavailable × light/dark × English/Ukrainian, plus 200 % font, with the map slot replaced by a placeholder; wire `MapScreen` as the only destination of `android/app/`
- [ ] T047 [US2] Add the store-build check in `android/build-logic/`: task `verifyStoreArtifact` that runs after `assembleStoreRelease` (unsigned, R8 on) and fails if the APK contains classes of package `io.github.nerushkolya.locator.internal`, or if the merged manifest of any variant declares a permission matching `*LOCATION*`, `POST_NOTIFICATIONS` or `FOREGROUND_SERVICE*` (FR-019; the location permissions are allowed again by the feature that introduces them); prove both checks fail by temporarily referencing an `internal` class from `main` and by temporarily adding `ACCESS_FINE_LOCATION` ([research R-14](research.md#r-14-android-tests-and-screenshots-fr-016-testing-strategy-4))
- [ ] T048 [US2] Kotlin generator time-box, ≤ 60 min ([research R-13](research.md#r-13-kotlin-client-from-the-contract-adr-009)): OpenAPI Generator `kotlin` client with kotlinx.serialization against the whole `api/openapi.yaml`, outside the build; record "adopt later / do not adopt" and why in `docs/architecture/adr/ADR-009-contract-first-openapi.md`

### Android commands

- [ ] T049 [US2] Create `scripts/test-android.ps1` per [developer-commands contract](contracts/developer-commands.md#test-androidps1): `gradlew` (`.bat` on Windows) in `android/`: unit tests of both flavors, Roborazzi verify (`-RecordScreenshots` records instead), `lintInternalDebug`, `assembleStoreRelease` + `verifyStoreArtifact`, `assembleInternalDebug`. **Acceptance**: quickstart §8 (green run; a hard-coded string in `MapScreen` makes the script exit non-zero)
- [ ] T050 [P] [US2] Create `scripts/install-phone.ps1` per [developer-commands contract](contracts/developer-commands.md#install-phoneps1): port from `infra/.env` (`LOCATOR_HTTP_PORT`, default `8080`); `assembleInternalDebug -Plocator.serverPort=<port>`; for each device in state `device` (or `-Serial`): `adb install -r`, `adb reverse tcp:<port> tcp:<port>` for serials not starting with `emulator-`, launch the activity; `-ReverseOnly`; fails when no device is connected. **Acceptance**: quickstart §7 install part and the re-plug edge case
- [ ] T051 [P] [US2] Create `scripts/logcat.ps1` per [developer-commands contract](contracts/developer-commands.md#logcatps1): logcat filtered by `--pid` of `io.github.nerushkolya.locator.internal`, re-resolving the pid when the app restarts; `-Serial`, `-Clear`. **Acceptance**: quickstart §7 log part (JSON records of the app visible, no coordinates or tokens). Then update `docs/architecture/android.md` §2 (`core:common` row), §3 (server address per flavor and device), `docs/quality/testing-strategy.md` §4 (screenshot baseline approach) and `docs/ops/dev-environment.md` §3 (`install-phone.ps1`, `logcat.ps1`); run quickstart §7–§8

**Checkpoint**: User Stories 1 and 2 work: quickstart §2–§8 pass.

---

## Phase 5: User Story 3 - Checks run automatically on every change (Priority: P2)

**Goal**: Pull requests and pushes to `main` run only the checks for changed paths plus the secret scan, each through the same script as locally; failing required checks block merging.

**Staged delivery**: `ci.yml` (T052) is not written in one go at the end. Its first version is created right after T027 (jobs `changes`, `contract`, `core`, `authz-matrix`, `workflows`, `secrets`; `core` and `authz-matrix` call the scripts that exist by then), the `delivery` job is added after T033, the `android` job after T051. Each step is a separate commit and keeps the Acceptance note of T052. T053–T055 stay in this group; T054 and T055 need the first workflow to exist, so the feature pull request may be opened as a draft as soon as the first version is pushed.

**Independent Test**: Quickstart §9: a contract-only pull request runs only `contract`, `authz-matrix` and `secrets`; a deliberately breaking contract change fails `contract` and names the change; total time under 15 minutes.

- [ ] T052 [US3] Create `.github/workflows/ci.yml` per [research R-15](research.md#r-15-continuous-integration-fr-015-fr-016) and `docs/ops/ci.md`: triggers `pull_request` and `push` to `main`; `permissions: {}` at workflow level, per-job `contents: read`; `concurrency` per ref with `cancel-in-progress`; job `changes` with a path-filter action pinned by full commit SHA (paths: `android` — `android/**`, `api/openapi.yaml`, `scripts/test-android.ps1`; `core` — `backend/core/**`, `infra/**`, `scripts/test-backend.ps1`, `scripts/lib/**`; `delivery` — `backend/delivery/**`, `infra/**`, `scripts/test-backend.ps1`, `scripts/lib/**`; `contract` — `api/**`, `scripts/test-contract.ps1`; `authz-matrix` — `backend/core/**`, `backend/delivery/**`, `api/**`; `workflows` — `.github/**`); every job filtered with `if:` on the outputs of `changes` (not workflow-level `paths`, so a skipped required job does not block merging); jobs `android` (JDK 21, Gradle setup action with cache read-only on pull requests, `pwsh scripts/test-android.ps1`), `core` / `delivery` (`pwsh scripts/test-backend.ps1 -Service <name>`), `authz-matrix` (`pwsh scripts/test-backend.ps1 -AuthzMatrix`, both services), `contract` (`fetch-depth: 0`, `pwsh scripts/test-contract.ps1`, base = `github.event.before` on push), `workflows` (zizmor), `secrets` (gitleaks Docker image pinned by digest, always); test reports and APKs uploaded as artifacts with `retention-days: 7`; every third-party action pinned by SHA with the version in a comment. **Acceptance**: quickstart §9 (verified in T054)
- [ ] T053 [P] [US3] Create `.github/dependabot.yml`: weekly updates for `gradle` (`/android`), `composer` (`/backend/core`), `gomod` (`/backend/delivery`), `docker` (`/infra/postgres`, `/backend/core`, `/backend/delivery`), `github-actions` (`/`)
- [ ] T054 [US3] Open the feature pull request; verify on it and on two throw-away pull requests (contract-only change; breaking contract change) that only the expected jobs run, that the breaking change fails `contract` with the rule name, and that a green run finishes in under 15 minutes (SC-005); update `docs/ops/ci.md` §1–§2 (jobs that exist from stage 1, the extended path filters, `backoffice` and `e2e-backend` arriving later) and add to the "Consequences" of `docs/architecture/adr/ADR-025-developer-commands-powershell-scripts.md` that `e2e-backend.ps1` is created with the first end-to-end scenario (stage 3)
- [ ] T055 [US3] 👤 Owner: in GitHub settings protect `main` (no force push, no deletion, pull request required, required checks: the `ci.yml` jobs including `authz-matrix` and `secrets`, squash merge only); jobs skipped by their `if:` count as passed, so path filtering does not block merging

**Checkpoint**: User Stories 1–3 work; `main` is protected.

---

## Phase 6: User Story 4 - Diagnose the environment (Priority: P3)

**Goal**: One read-only command reports each automated environment check with a status and a hint.

**Independent Test**: Quickstart §1: all `OK` on a prepared PC (row 11 `WARN` while the stack is down, row 13 `WARN` without a phone); with Docker Desktop stopped the engine check is `FAIL` and the exit code is `1`.

- [x] T056 [US4] Create `scripts/doctor.ps1` per [developer-commands contract](contracts/developer-commands.md#doctorps1): rows 1–11 and 13 of `docs/ops/env-checklist.md`, one `Write-CheckLine` each; row 7 fails below the minimum Compose version that supports `up --wait --wait-timeout` (version confirmed in Docker documentation in the task) and prints the required version; row 8 fails below 4 GB; row 9 accepts ports published by Compose project `locator`; row 10 compares `infra/.env` with `infra/.env.example`; row 11 `WARN` "stack not running, run up.ps1" when no `locator` container runs, `FAIL` when it runs and `/health` is not `200`; row 13 `WARN` when `adb devices` lists no device; exit `1` only on `FAIL`; never starts, stops or installs anything. **Acceptance**: quickstart §1 including both negative checks
- [x] T057 [US4] Update `docs/ops/env-checklist.md` row 11 (the script reports, it does not start the stack) and run the negative checks of quickstart §1

**Checkpoint**: All user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end confirmation and the stage Definition of Done.

- [ ] T058 Run gitleaks locally (same image as CI) over the branch and confirm the scrubbing tests of `core`, `delivery` and the app are all part of the default test commands; fix any finding
- [ ] T059 Run quickstart §1–§9 on the development PC from a fresh clone, following only `docs/ops/dev-environment.md` and `README.md` (SC-001); add a short "Getting started" section with links to `README.md` if a step is missing; fix every gap found
- [ ] T060 👤 Demo (roadmap stage 1 Definition of Done): owner runs `up.ps1` and `install-phone.ps1` on both test phones and on emulators with API 29 and the latest API; "Server reachable" within 5 s (SC-003); owner judges the map of the home city at street and city level with Ukrainian labels (SC-004) and the decision is recorded in `docs/architecture/adr/ADR-018-maps-maplibre-openfreemap.md`
- [ ] T061 Record the memory baseline per quickstart §10 (`docker stats --no-stream` for every container, no load) next to NFR-SRV-06 in `docs/quality/nfr.md` with the date (FR-018, SC-006 "under 1 GB"); set the feature status to `Done` in `specs/001-walking-skeleton/spec.md` and `specs/README.md`; mark stage 1 done in `docs/roadmap/roadmap.md`; squash-merge the pull request

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup; blocks all user stories
- **US1 (Phase 3)**: depends on Phase 2
- **US2 (Phase 4)**: depends on Phase 2; JVM work (T035–T049) needs nothing from US1; the on-device checks of T050/T051 and quickstart §7 need the running stack from US1
- **US3 (Phase 5)**: needs the scripts it calls: T032, T033 (US1) and T049 (US2)
- **US4 (Phase 6)**: depends on Phase 2 and T008; row 11 is meaningful once `core` exists (T014)
- **Polish (Phase 7)**: after all stories

### User Story Dependencies

- **US1 (P1)**: independent after Phase 2
- **US2 (P1)**: independent for build and JVM tests; uses US1's stack for the device demo
- **US3 (P2)**: wraps the commands of US1 and US2
- **US4 (P3)**: independent; executed in the Infrastructure group right after T010 because it shares the script helpers

### Within Each User Story

- Test tasks before the implementation they cover (T015→T016, T017→T018, T020/T021→T022, T023–T025→T026, T040–T044 test-then-implement)
- Contract (T003) before any core endpoint; T019 (validator) before T021, T024
- Compose services after their Dockerfiles (T011–T013 → T014, T028)

### Parallel Opportunities

- Phase 2: T004 and T005 in parallel after T003
- US1: T015, T017 in parallel; T020, T021 in parallel; T023, T024, T025 in parallel; the delivery track T028–T031 in parallel with the core track T011–T027
- US2: T037, T038, T039 in parallel after T036; T050, T051 in parallel
- US3: T053 in parallel with T052

---

## Parallel Example: User Story 1

```text
# Core and delivery tracks side by side after T014:
Task: "T015 Write failing ScrubbingProcessorTest in backend/core/tests/Unit/Platform/Logging/"
Task: "T029 Write failing scrub_test.go in backend/delivery/internal/logging/"

# Readiness tests together:
Task: "T023 ReadinessProbeTest (unit, fakes)"
Task: "T024 HealthIntegrationTest (postgres-test)"
Task: "T025 BaselineTest (postgres-test)"
```

## Parallel Example: User Story 2

```text
Task: "T037 core:designsystem from docs/design/theme/"
Task: "T038 core:testing fakes and dispatcher rule"
Task: "T039 core:model types"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 and Phase 2
2. Phase 3 (US1): healthy stack, public remote configuration, backend and contract tests
3. **Stop and validate**: quickstart §2–§6

### Incremental Delivery

1. Infrastructure group: T001–T010, then US4 (T056–T057)
2. Backend group: T011–T034 → US1 complete
3. Android group: T035–T051 → US2 complete
4. CI group: first `ci.yml` version right after T027, `delivery` job after T033, `android` job after T051 (all T052); then T053–T055 → US3 complete, `main` protected
5. Finish and demo: T058–T061 → stage 1 Definition of Done

---

## Notes

- [P] tasks touch different files and have no dependency on an unfinished task
- Every version "latest stable" is checked on its official release page in the task and pinned (lock file, version catalog, image digest)
- A task is done when its tests are green, it is checked off here, the documents it names are updated and the change is committed
- Do not start a task of a later group before the current group's checkpoint passes

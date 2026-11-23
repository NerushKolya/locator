# Research: Walking skeleton

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-11-23

Open technical questions of the walking skeleton and the decisions taken. Versions marked *verified* were checked against the official source on the research date; versions marked *not verified* are pinned (exact version and image digest) by the task that introduces them, after checking the official source again.

## R-01. Server runtimes and versions

- **Decision**:
  - `core`: PHP 8.5 on the official FrankenPHP image (`dunglas/frankenphp`, Debian variant, `php8.5`), classic mode ([ADR-010](../../docs/architecture/adr/ADR-010-frankenphp-runtime-and-edge-proxy.md)). Symfony: the latest stable 8.x minor on the day the core is created. On the research date that is 8.1; 8.2 is scheduled for November 2026 and listed as not yet released ([Symfony 8.2 release page](https://symfony.com/releases/8.2), [Symfony releases](https://symfony.com/releases)). If 8.2 is released before the task starts, 8.2 is used.
  - `delivery`: Go 1.26 (*verified*: released 2026-02-10, [Go 1.26 release notes](https://go.dev/doc/go1.26)); the latest 1.26.x patch from [go.dev/dl](https://go.dev/dl/) at implementation time.
  - FrankenPHP image tag: *not verified* (official tag list not reached during research); pinned by digest.
- **Rationale**: matches ADR-001, ADR-002 and ADR-010; Symfony's own policy is to start on the current stable minor and follow minors.
- **Alternatives considered**: Symfony 7.4 LTS (longer support, but ADR-001 chose "current stable 8.x"); FrankenPHP worker mode (deferred by ADR-010).

## R-02. Validating core responses against an OpenAPI 3.1 contract (FR-003)

The contract uses OpenAPI 3.1 constructs (`type: [string, 'null']`, `const`, `examples`), so a validator that only understands OpenAPI 3.0 fails on the contract file even for endpoints that do not use them.

- **Decision**: evaluate in this order in the first core API test task and keep the first that loads the **whole** `api/openapi.yaml` and validates `GET /health` and `GET /v1/config` responses, including a deliberately wrong response that must fail:
  1. **Gesso** (`studio-design/gesso`): states OpenAPI 3.0/3.1/3.2 contract testing, a Symfony adapter and PHPUnit 12/13 support ([repository](https://github.com/studio-design/gesso), [Symfony guide](https://studio-design.github.io/gesso/contract-testing-symfony)). Licence and maintenance activity: *not verified*.
  2. **`league/openapi-psr7-validator`** with the Symfony PSR-7 bridge: the widely used option, but documented for OpenAPI 3.0.x; 3.1 support through the community fork of its spec reader is *not verified*.
  3. **Fallback**: a JSON Schema draft 2020-12 validator (OpenAPI 3.1 schemas are JSON Schema 2020-12) applied to the response schema resolved from the contract by a small test helper.
- **Rationale**: [testing strategy](../../docs/quality/testing-strategy.md) defers the library choice to stage 1 and marks 3.1 support as unverified; an evaluation with a negative test is cheaper than a wrong choice found in stage 3.
- **Alternatives considered**: generating server code from the contract (rejected by ADR-009); limiting the contract to OpenAPI 3.0 (ADR-009 allows a subset only if no tool handles a 3.1 construct).
- **Record**: the chosen library goes into [testing strategy §3](../../docs/quality/testing-strategy.md).

## R-03. Database image, schemas, roles and migrations (FR-004, FR-005)

- **Decision**:
  - `infra/postgres/Dockerfile` as in [ADR-020](../../docs/architecture/adr/ADR-020-database-image-postgis-multiarch.md): `FROM postgres:18-bookworm` pinned by digest, `postgresql-18-postgis-3` from the PostgreSQL apt repository.
  - An init script in the image (run by the official entrypoint only on an empty data directory) creates the PostGIS extension, schemas `core` and `delivery`, and the roles from [data model §1](../../docs/architecture/data-model.md): `core_migrator` (owns schema `core`), `core_app` (read/write on `core`, default privileges granted by `core_migrator`), `delivery_app` (owns schema `delivery`). Passwords come from environment variables. `backoffice` schema and role are created when the back office is built.
  - Doctrine Migrations for `core`; the metadata table lives in schema `core`. The first migration is a baseline that asserts the PostGIS extension is present (fails with a clear message otherwise) and creates no tables.
  - Migrations run automatically at start as a one-off Compose service `core-migrate` (same image as `core`, role `core_migrator`); `core` starts only after it completed successfully (`depends_on: condition: service_completed_successfully`). This matches the production rule "migrations run as a separate step before a new version starts" ([deployment](../../docs/ops/deployment.md)).
- **Rationale**: one image everywhere (FR-004); ownership rule O1 holds from the first table; an existing volume with an older schema is migrated forward or the start fails with Doctrine's error (edge case "older schema"), nothing is dropped.
- **Alternatives considered**: running migrations in the `core` entrypoint (couples HTTP start to DDL rights; the app role would need DDL); creating roles in a migration (a migration cannot create the role it runs as; superuser credentials would reach the core).

## R-04. Health semantics (FR-001, clarification of 2026-11-23)

- **Decision**:
  - `core` `GET /health`: `200 {"status":"ok"}` when a `SELECT 1` succeeds and Doctrine Migrations reports no new (unexecuted) migrations; otherwise `503 {"status":"down"}`. A short DB timeout (≈ 1 s) keeps the probe fast. `degraded` is reserved and not produced in this feature.
  - `delivery` `GET /health`: `200 {"status":"ok"}` while the process serves HTTP.
  - Compose health checks call the same endpoints. The `delivery` image has no shell or curl, so the binary has a `-healthcheck` flag that requests its own `/health` and exits 0/1. `core` uses `php -r` with `file_get_contents`, which returns false on `503`.
  - Health requests are logged at debug level only (health checks run every few seconds).
- **Rationale**: one operation per service as in the contract; readiness includes what the core needs to serve requests.
- **Doc impact**: [backend §10](../../docs/architecture/backend.md) still says "liveness and readiness"; it is updated to the clarified single-operation semantics.

## R-05. Local stack layout and ports (FR-006, FR-007)

- **Decision**:
  - One Compose project `locator` in `infra/docker-compose.yml`: `postgres`, `core-migrate`, `core`, `delivery`; profile `test`: `postgres-test` (data on `tmpfs`, no host port), `core-test`, `delivery-test`.
  - Host ports are bound to `127.0.0.1` only: `${LOCATOR_HTTP_PORT:-8080}` → `core:8080`, `${LOCATOR_DB_PORT:-15432}` → `postgres:5432`. `delivery` publishes no host port in this feature. The emulator's `10.0.2.2` and `adb reverse` both reach the host loopback, so nothing needs to listen on other interfaces.
  - Source code is copied into the images at build time (`up.ps1` runs `docker compose up --build --wait`); there are no source bind mounts in this feature. Dependency layers (`composer install`, `go mod download`) are cached by Docker. A bind-mount development mode with dependency folders in named volumes ([development environment §1](../../docs/ops/dev-environment.md), risk R-12) is introduced when PHP code grows (stage 3) and is decided there.
  - Stored data lives in the named volume `pgdata`; `down.ps1` never removes volumes.
- **Rationale**: identical images for local runs, tests and CI; no Windows bind-mount slowness; the PC shares one localhost with other stacks (mirrored WSL networking), so only Locator's own ports are configurable.
- **Alternatives considered**: bind mounts now (slow on a Windows drive, more moving parts for a skeleton); publishing `delivery` (not needed until the edge proxy routes `/v1/stream` in stage 4b).

## R-06. Edge proxy scope in this feature

- **Decision**: the core's Caddyfile only serves the PHP application on `:8080` (HTTP, no TLS locally). Routing of `/v1/stream` to `delivery`, blocking of `/internal/**` and `/admin/**`, and TLS are added in the features that introduce those paths (4b, 8).
- **Rationale**: nothing behind those routes exists yet; untested proxy rules would be dead configuration.

## R-07. Remote configuration in the core (FR-002)

- **Decision**:
  - Lives in the `Platform` shared kernel (`Platform/RemoteConfig`): it is a cross-module document (engine parameters, limits of Groups/Places/Alerts, legal links of Identity), read by every client before sign-in.
  - Built-in defaults only (version `1`); see [data-model.md](data-model.md). `operatorMode` comes from `OPERATOR_MODE` (`off` by default); legal URLs are built from `LOCATOR_PUBLIC_BASE_URL`.
  - Strong `ETag` = hash of the serialized document; `If-None-Match` match → `304` with no body.
  - The operation is public: contract change in [contracts/openapi-changes.md](contracts/openapi-changes.md).
- **Rationale**: the [architecture overview](../../docs/architecture/overview.md) lists "remote engine configuration" under Locations, but the document also carries non-location values; the back office later publishes the whole document through the admin API ([backend §7](../../docs/architecture/backend.md)).
- **Doc impact**: overview §6 table is updated (Platform owns the remote configuration document; Locations owns the meaning of engine parameters).
- **Alternatives considered**: a database table now (no editor until stage 9; built-in defaults are what FR-002 asks for).

## R-08. Structured logs and scrubbing (FR-020, principle V)

- **Decision**: one log record format and one scrubbing rule set for all three runtimes, specified in [contracts/log-records.md](contracts/log-records.md):
  - JSON, one record per line: `ts`, `level`, `service`, `msg`, `requestId` (when in a request), and allow-listed context fields.
  - Scrubbing in two layers: (1) by key — values of keys naming coordinates, place names, tokens, e-mail addresses and invitation codes are replaced; (2) by pattern — coordinate pairs, JWT-like tokens, `Bearer …` values and e-mail addresses inside any string are replaced. Place names cannot be recognised by pattern and are therefore only ever logged under keys, which layer 1 removes.
  - `core`: Monolog JSON formatter to stdout + a Monolog processor. `delivery`: `log/slog` JSON handler wrapped by a scrubbing handler. App: a small `Logger` in `core:common` with an Android `Log` sink; release builds drop debug records.
  - The same test vectors (listed in the contract) are used by the tests of each runtime.
  - `requestId`: taken from `X-Request-Id` if present and well-formed, otherwise generated; returned in the response header (as in the [API guide](../../docs/api/README.md)).
- **Rationale**: the constitution requires scrubbing tests "in every service and in the app"; a shared vector list keeps the three implementations equivalent.
- **Alternatives considered**: Timber on Android (a new dependency for a thin wrapper); scrubbing only by key (misses values inside messages).
- **Out of scope**: error tracking SDKs (ADR-023) arrive with production; their `beforeSend` filters reuse these rules.

## R-09. Contract checks (FR-017)

- **Decision**:
  - Lint: Redocly CLI from its official Docker image with `api/redocly.yaml` (the `recommended` rule set; deviations listed with reasons in that file). Image tag: *not verified*, pinned by digest.
  - Breaking changes: oasdiff from the official `tufin/oasdiff` image, `breaking --fail-on ERR`, base = `api/openapi.yaml` at the merge base with `origin/main` (on pushes to `main`: the previous commit). OpenAPI 3.1 support in oasdiff is labelled beta in its README, with a dedicated 3.1 image tag ([oasdiff repository](https://github.com/tufin/oasdiff), [Docker Hub tags](https://hub.docker.com/r/tufin/oasdiff/tags)); the task picks the tag that parses the contract and reports a deliberately broken copy (removed required response field) as `ERR`.
  - If the base has no contract file, the breaking check is skipped with a notice (first commit only).
- **Expected result for this feature's own change**: making `/v1/config` public (removing the security requirement and the `401` response) is not a breaking change for clients; the check must pass. If oasdiff classifies it as `ERR`, the task records the exact rule and the decision in the pull request (per ADR-009 the contract is the source of truth; a waiver file is used only for that rule).
- **Alternatives considered**: Spectral (equivalent linter; Redocly is already named in the testing strategy); openapi-diff (Java, weaker 3.1 support).

## R-10. Android build and modules (FR-011…FR-014, FR-019)

- **Decision**:
  - Android Gradle Plugin 9.x with built-in Kotlin (the separate Kotlin Android plugin is not applied from AGP 9.0 on, [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)); latest stable AGP, Gradle, Kotlin, Compose BOM, Hilt, KSP, Ktor and kotlinx.serialization on the day the project is created: *not verified*, fixed in `gradle/libs.versions.toml`.
  - `compileSdk`/`targetSdk` 36, `minSdk` 29 (constitution).
  - `applicationId` `io.github.nerushkolya.locator` (owner's decision 2026-11-23), root package the same; `internal` flavor adds the suffix `.internal`, so both flavors can be installed side by side.
  - Convention plugins in `android/build-logic` (`locator.android.application`, `locator.android.library`, `locator.android.feature`, `locator.jvm.library`, `locator.android.compose`, `locator.hilt`).
  - Modules in this feature: `app`, `core:common` (new, pure Kotlin: `Logger`, scrubbing, dispatcher qualifiers), `core:model`, `core:network`, `core:data`, `core:designsystem` (from `docs/design/theme/`, package moved under the root package), `core:testing`, `feature:map`. Dependency rules of [Android architecture §2](../../docs/architecture/android.md) apply; `core:common` may be used by every module and depends on nothing.
  - No location, notification or background permission is declared (FR-019); only `INTERNET` and `ACCESS_NETWORK_STATE`.
  - `allowBackup="false"` and data-extraction rules excluding everything ([Android architecture §8](../../docs/architecture/android.md)).
- **Rationale**: ADR-011; `core:common` is needed because both `core:network` and `core:data` log and must share one scrubbing implementation, and `core:model` is reserved for domain types.
- **Doc impact**: Android architecture §2 gets the `core:common` row.

## R-11. Server address per flavor and device (FR-011, FR-012, US-2)

- **Decision**:
  - `store`: a fixed `BuildConfig.SERVER_URL` (placeholder `https://locator.example.org` until the production stage) and no code path that changes it.
  - `internal`: the port is a Gradle property `locator.serverPort` (default `8080`; `install-phone.ps1` passes `LOCATOR_HTTP_PORT` from `infra/.env`). At runtime a `ServerAddressProvider` in the `internal` source set chooses `http://10.0.2.2:<port>` on an emulator and `http://localhost:<port>` on a phone (with `adb reverse`). Emulator detection uses build properties (`Build.FINGERPRINT`, `Build.HARDWARE`, `Build.PRODUCT`) behind an interface, unit-tested with fake values.
  - A run-time server address editor is not built in this feature (FR-011 says "MAY"); the address is changed by rebuilding with another port.
  - Clear-text HTTP is allowed only in `debug` builds and only for `localhost`, `127.0.0.1` and `10.0.2.2` (network security configuration in the `debug` source set).
- **Rationale**: one install command for phones and emulators, matching acceptance scenarios US-2 #1 and #2, with no settings screen to build and test.
- **Alternatives considered**: `adb reverse` on emulators too (contradicts US-2 #2, which names the emulator's host address); a DataStore-backed address field (needs `core:datastore` and a screen only for developers).

## R-12. Map integration (FR-013, ADR-018 open point)

- **Decision**:
  - Prototype with **MapLibre Compose** (`org.maplibre.compose`), the MapLibre organisation's Compose wrapper over MapLibre Native ([getting started](https://maplibre.org/maplibre-compose/getting-started/)). It is a 0.x library; the version shown in its guide on the research date is 0.20.0, Maven Central listing differs: *not verified*.
  - Keep it only if, within the prototype task, it: builds with the project's Compose BOM and AGP; accepts a style URL at runtime; shows the attribution; reports style-load failure (needed for the placeholder); adds an acceptable amount to the APK size (NFR-APP-06). Otherwise use MapLibre Native Android through `AndroidView`.
  - Default camera: centre of Kyiv (Maidan Nezalezhnosti, 50.4501, 30.5234) at zoom 11, a constant in `feature:map` (clarification of 2026-11-23).
  - Default style URL served by the core: OpenFreeMap "Liberty" (`https://tiles.openfreemap.org/styles/liberty`; URL to be confirmed on [openfreemap.org](https://openfreemap.org/) in the task).
  - Screenshot tests replace the map with a placeholder slot (native rendering does not run on the JVM).
- **Rationale**: Android architecture §7 leaves the Compose integration to this prototype; a Compose-first API is simpler for a developer new to Android, the `AndroidView` fallback is well known.
- **Record**: the outcome and the owner's map-quality judgement (SC-004) go into [ADR-018](../../docs/architecture/adr/ADR-018-maps-maplibre-openfreemap.md) in the demo item.

## R-13. Kotlin client from the contract (ADR-009)

- **Decision**: hand-written DTOs with kotlinx.serialization for the two operations of this feature. A generator is tried in a time-boxed task (≤ 60 min): OpenAPI Generator `kotlin` client with kotlinx.serialization, judged on whether it parses the whole 3.1 contract, the size and readability of the output, and Ktor support. The result (adopt later / do not adopt) is recorded in ADR-009.
- **Rationale**: ADR-009 asks to try a generator in the walking skeleton; with two DTOs the hand-written path is cheaper either way, and the decision only matters from stage 3.
- **Contract check on the client side**: a JVM test decodes example responses that match the contract schemas and fails on missing required fields.

## R-14. Android tests and screenshots (FR-016, testing strategy §4)

- **Decision**: JUnit 4/5 with kotlinx-coroutines-test and Turbine for view models and repositories; Compose UI tests on Robolectric; Roborazzi screenshot tests of the map screen states (reachable, not reachable, map unavailable) in light and dark themes, Ukrainian and English, and 200 % font scale; Android Lint with hard-coded text as an error; a store-build check.
- **Risk and mitigation**: Robolectric rendering can differ between Windows and Linux. Baselines are recorded on the developer PC; if CI on Linux reports pixel differences for unchanged screens, a small change threshold is set, and if that is not enough, baselines are produced by CI and committed from its artifact. The chosen option is recorded in the testing strategy.
- **Store-build check**: `assembleStoreRelease` (unsigned) must succeed with R8; a unit test in the `store` variant asserts the server address is the fixed one and not configurable; a Gradle task asserts that the store APK contains no classes from the `internal` source set package.

## R-15. Continuous integration (FR-015, FR-016)

- **Decision**: `.github/workflows/ci.yml` as in [CI](../../docs/ops/ci.md), limited to the code bases that exist:
  - `changes` (path filter action pinned by SHA) → `android`, `core`, `delivery`, `contract`, `authz-matrix`, `workflows` (zizmor, when `.github/**` changed), `secrets` (gitleaks Docker image, always).
  - `backoffice` and `e2e-backend` jobs are added with their code (stage 9 and the first end-to-end scenario in stage 3).
  - Every job calls the developer script; `permissions: {}` at workflow level; `concurrency` with `cancel-in-progress`; Gradle cache read-only on pull requests; artifacts kept 7 days.
  - `.github/dependabot.yml`: weekly updates for Gradle, Composer, Go modules, Docker base images and GitHub Actions (ADR-020 relies on it for database image updates).
- **Authorization matrix in this feature**: the suite exists as a data table in each service — `core`: `/health` and `/v1/config`; `delivery`: `/health` (anonymous caller → `200` for each) — so the required job exists from the first pull request, covers every endpoint, and grows with every new one.
- **Required checks and path filters**: filtering is done per job (`if:` on the outputs of `changes`), not with workflow-level `paths`, so a required job that is skipped reports as skipped and does not block merging.
- **Rationale**: SC-005 (CI under 15 minutes, path-filtered); required checks can be set on GitHub only for jobs that exist.

## R-16. Developer scripts (FR-008, FR-009, ADR-025)

- **Decision**:
  - Scripts work on Windows PowerShell 5.1 and PowerShell 7; shared helpers in `scripts/lib/Locator.psm1` (reading `.env` files, check output lines, running native commands with exit-code checks, OS detection without `$IsWindows`, which 5.1 lacks).
  - `up.ps1` validates `infra/.env` against `infra/.env.example` (every name present and non-empty) and checks the two host ports before starting; then `docker compose up --build --wait --wait-timeout 180`; prints the URLs.
  - `doctor.ps1` implements the **auto** rows of the [environment checklist](../../docs/ops/env-checklist.md). It never starts or stops anything: row 11 ("stack healthy") reports `WARN` with a hint when the stack is not running and `FAIL` when it runs but `/health` is not `200`. Row 13 is `WARN` when no phone is listed.
  - `e2e-backend.ps1` is not created in this feature (no end-to-end scenario before sign-in exists); the quickstart covers the manual end-to-end check.
  - Interfaces: [contracts/developer-commands.md](contracts/developer-commands.md).
- **Doc impact**: environment checklist row 11 wording.
- **How the scripts are tested**: each script task names the quickstart scenario that is run before (fails) and after (passes) the change ([plan: test-first for tooling tasks](plan.md#test-first-for-tooling-tasks)); CI then runs the scripts on PowerShell 7 at every change.
- **Alternatives considered**: Pester tests for the scripts (a new tool class needing an ADR; reconsidered when `scripts/lib` grows logic worth unit-testing).

## R-17. Memory baseline (FR-018, SC-006)

- **Decision**: after `up.ps1` with no load, `docker stats --no-stream` is recorded for every container; the numbers and the date go into [NFR](../../docs/quality/nfr.md) next to NFR-SRV-06. Done in the demo item.

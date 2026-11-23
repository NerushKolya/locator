# Implementation Plan: Walking skeleton

**Branch**: `001-walking-skeleton` | **Date**: 2026-11-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-walking-skeleton/spec.md`

## Summary

Every part of the system exists, builds, runs and is tested in CI, with no product features. The server side is a Docker Compose stack: the project's PostgreSQL 18 + PostGIS image with schemas and roles, a one-off migration step (Doctrine Migrations), `core` (Symfony on FrankenPHP) serving `/health` and the public `GET /v1/config` validated against the OpenAPI contract, and `delivery` (Go) serving `/health`. PowerShell scripts wrap every developer action and are called unchanged by a path-filtered GitHub Actions workflow. The Android project (flavors `internal`/`store`, version catalog, convention plugins, `core:*` skeleton, `feature:map`) shows whether the server is reachable and a MapLibre map drawn with the style URL from the remote configuration. All three runtimes write structured JSON logs through one scrubbing rule set with shared test vectors. Decisions: [research.md](research.md).

## Technical Context

**Language/Version**: PHP 8.5 + Symfony latest stable 8.x (`core`); Go 1.26 (`delivery`); Kotlin with Android Gradle Plugin 9.x built-in Kotlin (app); SQL (PostgreSQL 18); PowerShell 5.1/7 (scripts); YAML (Compose, GitHub Actions, OpenAPI 3.1). Exact versions pinned by the introducing task ([research R-01, R-10](research.md)).

**Primary Dependencies**: FrankenPHP (classic mode), Doctrine DBAL + Doctrine Migrations, Monolog, an OpenAPI 3.1 response validator for PHPUnit (R-02); Go standard library (`net/http`, `log/slog`); Jetpack Compose + Material 3, Hilt + KSP, Ktor Client (OkHttp engine) + kotlinx.serialization, MapLibre Compose or MapLibre Native (R-12); Redocly CLI, oasdiff, gitleaks, zizmor as Docker images or pinned actions.

**Storage**: PostgreSQL 18 + PostGIS 3.6 from `infra/postgres/Dockerfile`; schemas `core` and `delivery`, roles `core_migrator`, `core_app`, `delivery_app`; only the Doctrine metadata table and a baseline migration ([data-model §4](data-model.md)). No storage in the app.

**Testing**: PHPUnit (unit, integration against `postgres-test`, API tests with contract validation, authorization matrix); `go test`; JUnit + coroutines-test + Turbine, Compose UI tests on Robolectric, Roborazzi screenshots, Android Lint, store-build check; Redocly lint + oasdiff breaking check. Backend tests run only in containers of the Compose `test` profile.

**Target Platform**: Developer PC: Windows 11, Docker Desktop (WSL 2 engine, `amd64`), Android Studio; CI: GitHub-hosted Ubuntu runners with PowerShell 7; Android 10 (API 29) to API 36 on two test phones and emulators. Production (`arm64`) is out of scope; images stay multi-architecture-ready.

**Project Type**: Mobile app + API (Android client, two server services, one database) with developer tooling.

**Performance Goals**: Warm `up.ps1` to healthy ≤ 3 min (SC-002); app shows the server status ≤ 5 s after opening (SC-003); CI ≤ 15 min per pull request (SC-005).

**Constraints**: No backend toolchains on the host (Docker only); no source bind mounts in this feature (R-05); host ports configurable and bound to `127.0.0.1` because the PC shares one localhost with other stacks; scripts compatible with PowerShell 5.1; no location, notification or background permission (FR-019); $0 and no vendors from the Russian Federation; every committed text in English.

**Scale/Scope**: 3 runtime containers + 1 one-off migration container + 3 test containers; 2 HTTP operations on `core`, 1 on `delivery`; 8 Android modules, 1 screen; 7 scripts; 1 CI workflow + Dependabot configuration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | How this feature complies | Result |
|---|---|---|
| I. Consent of the located person | No location is collected; the app declares no location permission (FR-019) | Pass (not engaged) |
| II. Nothing hidden | No background work, no notifications; `store` flavor has a fixed server address and no `internal`-only code; no operator code exists yet; store-build check added now (R-14) | Pass |
| III. Group boundary and reciprocity | No personal data endpoints. The authorization matrix exists in both services from the start: `core` rows for `GET /health` and `GET /v1/config`, a `delivery` row for `GET /health` (anonymous → `200` each); `test-backend.ps1 -AuthzMatrix` runs both, and its CI job is required from the first pull request (R-15) | Pass |
| IV. Honest freshness | No positions or freshness yet | Pass (not engaged) |
| V. Privacy by minimization | Structured logs with one scrubbing rule set and tests in `core`, `delivery` and the app (FR-020, [log-records contract](contracts/log-records.md)); `/v1/config` and `/health` carry no personal data; no analytics or advertising SDKs; the tile provider seeing the phone's IP is already listed in the data inventory | Pass |
| VI. Store-policy compliance | No location permission and no foreground service in this feature; `allowBackup` off and clear-text limited to debug builds and local hosts (R-10, R-11) | Pass (not engaged) |
| VII. Contract-first and test-first | The contract change ([openapi-changes](contracts/openapi-changes.md)) is the first core task; lint + breaking-change check in CI; core responses validated against the contract in tests. Code tasks start with a failing automated test. Tooling tasks (scripts, Compose services, Gradle scaffolding, CI workflow) have no unit under test; their test is a named [quickstart](quickstart.md) scenario that is run before the task (and fails) and after it (and passes) — see [Test-first for tooling tasks](#test-first-for-tooling-tasks). No background location, so the device test matrix does not apply | Pass |
| VIII. Simplicity and zero cost | One Compose project, PostgreSQL as the only state, no broker or cache. No new service, tool class or dependency class beyond those already named in ADRs and the testing strategy: the OpenAPI validator (R-02) and the MapLibre Compose integration (R-12) are the stage-1 choices those documents defer; `core:common` is a module, not a dependency class | Pass |

Additional constraints: minSdk 29 / targetSdk 36 ✔; English-only repository ✔; `.env.example` with names only and gitleaks in CI ✔; versions pinned in lock files, the version catalog and image digests ✔.

**Gate result (pre-research)**: PASS. **Re-check after Phase 1 design**: PASS — the design adds no service, broker, permission or personal-data flow; the only contract change removes an authentication requirement from an operation that returns no personal data.

## Project Structure

### Documentation (this feature)

```text
specs/001-walking-skeleton/
├── spec.md                       # Approved specification
├── plan.md                       # This file
├── research.md                   # Decisions R-01…R-17
├── data-model.md                 # Remote configuration, health, local configuration, DB baseline, app state
├── quickstart.md                 # Manual validation scenarios
├── contracts/
│   ├── openapi-changes.md        # Change to api/openapi.yaml (/v1/config public, /health description)
│   ├── developer-commands.md     # Interface of scripts/*.ps1
│   └── log-records.md            # Log format, scrubbing rules, shared test vectors
├── checklists/requirements.md    # Spec quality checklist
└── tasks.md                      # Next step
```

### Source Code (repository root)

```text
api/
├── openapi.yaml                  # /v1/config public; /health description
└── redocly.yaml                  # Lint rules (recommended + listed deviations)

infra/
├── docker-compose.yml            # postgres, core-migrate, core, delivery; profile test: postgres-test, core-test, delivery-test
├── .env.example                  # Every name, no secret values
└── postgres/
    ├── Dockerfile                # FROM postgres:18-bookworm@digest + postgresql-18-postgis-3
    └── initdb/10-schemas-roles.sh

backend/core/
├── Dockerfile                    # FrankenPHP; targets: base, test, runtime
├── Caddyfile                     # :8080, PHP app only (R-06)
├── composer.json / composer.lock
├── bin/console
├── config/                       # framework, doctrine, doctrine_migrations, monolog, services
├── migrations/                   # Baseline migration
├── public/index.php
├── src/Platform/
│   ├── Health/                   # ReadinessProbe (DB + migrations), HealthController
│   ├── RemoteConfig/             # Built-in defaults, RemoteConfigController (ETag, 304)
│   ├── Logging/                  # Scrubbing processor, JSON formatter setup
│   └── Http/                     # Request id subscriber, problem+json for 404/500
└── tests/
    ├── Unit/                     # Scrubbing vectors, defaults, ETag
    ├── Integration/              # Readiness against postgres-test
    ├── Api/                      # WebTestCase + OpenAPI response validation
    └── AuthzMatrix/              # Data table: public operations

backend/delivery/
├── Dockerfile                    # Multi-stage: test target, static binary on a minimal base
├── go.mod / go.sum
├── cmd/delivery/main.go          # Config, HTTP server, graceful shutdown, -healthcheck flag
└── internal/
    ├── health/                   # /health handler + tests
    ├── logging/                  # slog JSON + scrubbing handler + vector tests
    └── requestid/                # X-Request-Id middleware

android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml     # Version catalog
├── gradle/wrapper/ + gradlew + gradlew.bat
├── build-logic/                  # Convention plugins
├── app/                          # Single activity, Hilt, flavors internal/store, manifest, network security config (debug)
│   └── src/{main,internal,store,debug,test}/
├── core/common/                  # Logger, scrubbing, dispatcher qualifiers (pure Kotlin)
├── core/model/                   # ServerStatus, RemoteConfig domain types
├── core/network/                 # Ktor client, DTOs, health and config API
├── core/data/                    # ServerStatusRepository, RemoteConfigRepository
├── core/designsystem/            # Theme from docs/design/theme/
├── core/testing/                 # Fake API server, test dispatchers
└── feature/map/                  # Map screen: server status + map + placeholder, MapViewModel

scripts/
├── lib/Locator.psm1
├── doctor.ps1  up.ps1  down.ps1
├── test-backend.ps1  test-contract.ps1  test-android.ps1
└── install-phone.ps1  logcat.ps1

.github/
├── workflows/ci.yml              # changes → android, core, delivery, contract, authz-matrix, workflows, secrets
└── dependabot.yml
```

**Structure Decision**: the repository layout of the [architecture overview §9](../../docs/architecture/overview.md). `backend/backoffice/`, the `core-worker` service, `e2e-backend.ps1` and the `backoffice`/`e2e-backend` CI jobs are not created in this feature: they arrive with the code they run (stage 9 and the first end-to-end scenario in stage 3). Core modules other than `Platform` are created when their first feature is built.

## Test-first for tooling tasks

Principle VII asks for a test before every task. For code (PHP, Go, Kotlin) the test is an automated test written first. For tooling — PowerShell scripts, Compose services and images, Gradle project setup, the CI workflow — the test is the acceptance scenario named in the task:

1. Before the change, run the named quickstart scenario (or the listed command) and confirm it fails for the expected reason (missing script, unhealthy service, missing job).
2. Make the change.
3. Run the same scenario again; it must pass, including its negative checks.
4. Mention the scenario and both outcomes in the commit message.

Once a tooling behaviour has logic worth unit-testing (for example `.env` validation in `scripts/lib`), adopting a PowerShell test framework is a separate decision recorded in a new ADR.

## Implementation phases

The order follows the work items of the stage; each phase ends with green tests and a commit. Detailed, test-first tasks are produced in `tasks.md`.

| Phase | Content | Key requirements | Exit check |
|---|---|---|---|
| A. Infrastructure | Database image and init script; Compose project with profiles; `.env.example`; `scripts/lib`, `doctor.ps1`, `up.ps1`, `down.ps1` (with placeholder `core`/`delivery` health until phase B) | FR-004…FR-010, US-1, US-4 | Quickstart §1, §2 (except `/v1/config`), §5 |
| B. Backend | Contract change first; `core` skeleton, readiness, remote configuration, request id, logs + scrubbing, contract validation, authorization matrix; `delivery` skeleton, health, logs + scrubbing; `test-backend.ps1`, `test-contract.ps1` | FR-001…FR-003, FR-010, FR-017, FR-020 | Quickstart §3, §4, §6 |
| C. Android | Gradle project, catalog, convention plugins, flavors; `core:*` skeleton and design system; server address per flavor and device; health and configuration clients; map screen with placeholder; logs + scrubbing; generator time-box (R-13); `test-android.ps1`, `install-phone.ps1`, `logcat.ps1` | FR-011…FR-014, FR-019, FR-020, US-2 | Quickstart §7, §8 |
| D. CI | `ci.yml`, Dependabot, pull request, required checks | FR-015, FR-016, US-3 | Quickstart §9 |
| E. Demo | Owner runs the demo on both phones and the emulator; memory baseline; map-quality decision | FR-018, SC-001, SC-003, SC-004, SC-006 | Quickstart §7, §10; roadmap stage 1 Definition of Done |

## Documentation updated in the same changes

| Document | Change | Phase |
|---|---|---|
| `api/openapi.yaml`, `docs/api/README.md` §3 | `/v1/config` public; `/health` description | B |
| `docs/architecture/backend.md` §10 | Single `/health` per service with the clarified readiness semantics (R-04) | B |
| `docs/architecture/overview.md` §6 | Remote configuration document owned by `Platform` (R-07) | B |
| `docs/quality/testing-strategy.md` §3, §4 | Chosen OpenAPI validator (R-02); screenshot baseline approach (R-14) | B, C |
| `docs/architecture/android.md` §2, §3, §7 | `core:common`; server address per flavor and device (R-11); MapLibre Compose integration result (R-12) | C |
| `docs/architecture/adr/ADR-009-…` | Kotlin generator time-box result (R-13) | C |
| `docs/ops/dev-environment.md` §3 | `.env` names, ports bound to `127.0.0.1`, emulator/phone addresses via `install-phone.ps1` | A, C |
| `docs/ops/env-checklist.md` row 11 | `doctor.ps1` does not start the stack (R-16) | A |
| `docs/ops/ci.md` §1 | Jobs present from stage 1; `backoffice` and `e2e-backend` later (R-15) | D |
| `docs/quality/nfr.md` NFR-SRV-06 | Memory baseline | E |
| `docs/architecture/adr/ADR-018-…` | Map quality and Compose integration outcome (SC-004) | E |
| `docs/design/design-system.md` §1 | Working name and `applicationId` (done in this planning step) | — |

Each changed document has a Russian copy that is updated after the English text is final.

## Risks

| Risk | Mitigation |
|---|---|
| No PHP validator handles the OpenAPI 3.1 contract (R-02) | Ordered evaluation with a negative test; JSON Schema 2020-12 fallback |
| oasdiff 3.1 support is beta (R-09) | Pick the tag that parses the contract and catches a planted breaking change; record any waiver |
| MapLibre Compose (0.x) conflicts with the Compose BOM or AGP (R-12) | Time-boxed prototype with explicit criteria; `AndroidView` fallback |
| Map quality for Ukraine (roadmap R-09) | Owner's judgement in the demo (SC-004); ADR-018 plan B |
| Screenshot differences between Windows and Linux (R-14) | Threshold, then CI-produced baselines |
| Port conflicts with other local stacks (mirrored networking) | `up.ps1` and `doctor.ps1` check ports first; ports bound to `127.0.0.1` and configurable |
| PowerShell 5.1 vs 7 differences | Shared helper module; scripts run on 5.1 locally and 7 in CI |
| Windows bind-mount slowness (roadmap R-12) | No bind mounts in this feature (R-05); decided again in stage 3 |

## Complexity Tracking

No constitution violations; nothing to justify.

# Locator

> Android app + backend. Work in progress.

Family location-sharing app: members of a group see each other's location on a map, based on explicit, mutual and revocable consent.

**Status:** blueprint (product, architecture, quality and operations documents) under review; implementation starts with the [roadmap](docs/roadmap/roadmap.md) stage 1.

## Principles

- Location is shared only with the explicit consent of the person being located; sharing can be paused or stopped in one tap.
- Nothing hidden: the app icon and the persistent notification are never hidden; every fresh-location request is visible to the person; no remote activation without the person's knowledge.
- A user can never get the location of someone they do not share a group with, and inside a group you see others only while you share yourself. Enforced on the server and covered by an authorization test suite that blocks merging.
- Honest freshness: the app always shows how old a position is and why a phone is silent when it is known.
- No ads, no data sales, no third-party analytics; no coordinates or tokens in logs or crash reports.
- Simple and free to run: one server, Docker Compose, PostgreSQL; every new service or tool needs an architecture decision record.

## Architecture in brief

| Part | Technology |
|---|---|
| Android app | Kotlin, Jetpack Compose, foreground location service, MapLibre |
| `core` | PHP, Symfony on FrankenPHP — domain-driven modular monolith, REST API |
| `delivery` | Go — server-sent event streams, viewer presence, push |
| `backoffice` | Laravel + Filament — operator tools (from the beta) |
| Database | PostgreSQL 18 + PostGIS |
| Hosting | One ARM VM in the EU (from the beta); local Docker Compose for development |

Details: [architecture overview](docs/architecture/overview.md) and [decision records](docs/architecture/adr/README.md).

## Repository layout

```
android/                 Android app (standalone Gradle project)
backend/core/            Symfony application (core + core-worker)
backend/delivery/        Go service
backend/backoffice/      Laravel application (from the beta)
api/openapi.yaml         REST contract (source of truth)
infra/                   Docker Compose files, images, proxy configuration
docs/                    Product, architecture, API, privacy, quality, operations, roadmap
specs/                   Feature specifications
scripts/                 Developer commands (PowerShell)
```

Code folders appear as the roadmap stages build them.

## Documentation

| Area | Start here |
|---|---|
| Product | [vision](docs/product/vision.md) · [PRD](docs/product/prd.md) · [feature matrix](docs/product/feature-matrix.md) · [glossary](docs/product/glossary.md) |
| Architecture | [overview](docs/architecture/overview.md) · [ADRs](docs/architecture/adr/README.md) · [API guide](docs/api/README.md) |
| Privacy | [data inventory](docs/privacy/data-inventory.md) · [permissions rationale](docs/privacy/permissions-rationale.md) · [store compliance](docs/privacy/store-compliance.md) |
| Quality | [NFR](docs/quality/nfr.md) · [testing strategy](docs/quality/testing-strategy.md) · [device test matrix](docs/quality/device-test-matrix.md) |
| Operations | [development environment](docs/ops/dev-environment.md) · [environment checklist](docs/ops/env-checklist.md) · [CI](docs/ops/ci.md) · [deployment](docs/ops/deployment.md) · [costs](docs/ops/costs.md) · [development workflow](docs/ops/development-workflow.md) |
| Plan | [roadmap](docs/roadmap/roadmap.md) · [risks](docs/roadmap/risks.md) · [specs](specs/README.md) |

## Development

Windows 11 PC: Android Studio natively; backend and database only in Docker. Set-up from zero: [development environment](docs/ops/dev-environment.md).

| Action | Command *(available from roadmap stage 1)* |
|---|---|
| Check the environment | `.\scripts\doctor.ps1` |
| Start / stop the local stack | `.\scripts\up.ps1` / `.\scripts\down.ps1` |
| Backend tests | `.\scripts\test-backend.ps1` |
| Contract checks | `.\scripts\test-contract.ps1` |
| Android tests | `.\scripts\test-android.ps1` |
| Install on a connected phone | `.\scripts\install-phone.ps1` |
| App logs | `.\scripts\logcat.ps1` |

Emulator → local backend: `http://10.0.2.2:8080`; phone over USB: `adb reverse tcp:8080 tcp:8080`.

## Conventions

- Everything in the repository is in English: code, comments, documentation, specs, commit messages.
- [Conventional Commits](https://www.conventionalcommits.org/); pull requests into `main` with required CI checks.
- Secrets are never committed: `.env` and `local.properties` are git-ignored; `.env.example` lists names only.
- Every significant technical decision is an ADR in [docs/architecture/adr](docs/architecture/adr/README.md).

## License

No license has been chosen yet; all rights reserved by the author.

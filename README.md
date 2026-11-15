# Locator

> Android app + backend API. Work in progress.

Family location-sharing app: members of a group see each other's location on a map, based on explicit, mutual and revocable consent.

Status: pre-implementation (research and design). Sections marked _TBD_ are filled in as decisions are made.

## Principles

- Location is shared only with explicit consent of the person being located; consent can be revoked in one tap.
- No hidden modes: no hidden icon or notification, no remote activation without the user's knowledge.
- A user can never access the location of someone they do not share a group with. Authorization is enforced per group and covered by tests.
- Keep it simple: modular monolith, no new infrastructure without a measurable reason.

## Repository layout (planned)

```
android/        Android app (Kotlin), standalone Gradle project
backend/        API server (language TBD), runs in Docker
api/            API contract (OpenAPI)
infra/          docker-compose and deployment configuration
docs/           Architecture and decision records
scripts/        Development helper scripts
```

## Development environment

- OS: Windows 11. The repository lives on the Windows filesystem.
- Android: Android Studio (bundled JDK) and the Gradle Wrapper in `android/`.
- Backend and database: run only in Docker (Docker Desktop, WSL2 engine). No backend toolchain is installed on the host.
- Database: PostgreSQL + PostGIS in Docker; data in a named volume.
- Dependency folders (`node_modules`, `vendor`, module caches) live in Docker volumes, not bind mounts.

### Networking in development

- Emulator to host backend: `http://10.0.2.2:8080`.
- Physical device over USB: `adb reverse tcp:8080 tcp:8080`, then `http://localhost:8080`.

## Commands

_TBD_ — build, run, test, install on device, read logs.

## Conventions

- All code, comments, documentation and commit messages are in English.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, ...).
- Secrets are never committed: use `.env` / `local.properties` (both git-ignored) and commit `.env.example` instead.
- Each significant technical decision is recorded as an ADR in `docs/adr/`.

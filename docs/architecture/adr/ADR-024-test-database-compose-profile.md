# ADR-024: Test database — a `test` profile in Docker Compose instead of Testcontainers

- Status: Accepted
- Date: 2026-11-17

## Context

Integration tests of the core and `delivery` need a real PostgreSQL with PostGIS: upserts "only if newer", idempotent inserts by point id, `ST_DWithin`, outbox with `LISTEN/NOTIFY`. Backend toolchains are not installed on the developer PC; tests themselves run inside containers. Testcontainers exists for PHP and Go, but a test process inside a container would need access to the Docker socket to start sibling containers.

## Options

1. **`postgres-test` service in the `test` profile of the same Compose project.** Tests run in a container next to it; data on `tmpfs`; isolation by transaction rollback (PHP) or a schema per test package (Go).
2. **Testcontainers.** Each test suite starts its own database; requires the Docker socket inside the test container, which adds privileges and Windows-specific setup.
3. **Shared development database.** Tests and manual work interfere.

## Decision

Option 1. `docker compose --profile test` starts `postgres-test` from the project database image ([ADR-020](ADR-020-database-image-postgis-multiarch.md)) with data on `tmpfs`, and the test runners of each service. Migrations run once per test session. The same command is used locally and in CI ([ADR-025](ADR-025-developer-commands-powershell-scripts.md)).

## Consequences

- No Docker socket inside containers; identical behaviour on Windows and on CI runners.
- Test isolation is the responsibility of the test harness (transactions or schemas), not of separate databases; tests that need committed data (outbox → `NOTIFY`) clean up their schema explicitly.
- Revisit if suites need fully disposable databases per test class.

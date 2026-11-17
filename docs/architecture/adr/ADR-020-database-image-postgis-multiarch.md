# ADR-020: Database image — own multi-architecture PostGIS image based on the official PostgreSQL image

- Status: Accepted
- Date: 2026-11-17

## Context

The database is PostgreSQL 18 with PostGIS 3.6 ([ADR-007](ADR-007-postgresql-postgis-storage.md)). Development runs on `amd64` (Windows PC), production on `arm64` ([ADR-019](ADR-019-hosting-oracle-always-free.md)). The commonly used image `postgis/postgis:18-3.6` is published for `linux/amd64` only ([Docker Hub: postgis/postgis](https://hub.docker.com/r/postgis/postgis/tags)), while the official `postgres:18` image is published for both architectures ([Docker Hub: postgres](https://hub.docker.com/_/postgres)). Development, CI and production must use the same database build.

## Options

1. **Own image `FROM postgres:18` + PostGIS packages from the PostgreSQL apt repository**, built for `amd64` and `arm64`. Same image everywhere; one small Dockerfile to maintain.
2. **`postgis/postgis` locally, another image in production.** Different builds in development and production.
3. **Emulated `amd64` image on ARM.** Slow and fragile.
4. **Managed PostgreSQL with PostGIS.** Not free at the needed size.

## Decision

`infra/postgres/Dockerfile` builds `FROM postgres:18-bookworm` (pinned by digest) and installs `postgresql-18-postgis-3` from the PostgreSQL apt repository. CI builds it for `linux/amd64` and `linux/arm64` on native runners and publishes it to the GitHub Container Registry. The same image is used by the local stack, the `test` profile ([ADR-024](ADR-024-test-database-compose-profile.md)) and production.

## Consequences

- One database build for all environments; extension versions are visible in one file.
- The image must be rebuilt for PostgreSQL and PostGIS security updates (weekly dependency update job).
- Major PostgreSQL upgrades are planned operations (dump and restore or `pg_upgrade`), never an automatic tag change.
- Revisit if the official PostGIS image starts publishing `arm64`.

# Architecture decision records

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [architecture overview](../overview.md)

An ADR records one significant decision: the problem, the options, the choice and its consequences. A new tool, service, dependency class or a change to an ownership rule from the [overview](../overview.md) needs an ADR.

## Statuses

| Status | Meaning |
|---|---|
| **Accepted** | Decided by the project owner |
| **Proposed** | Introduced by the blueprint; becomes accepted at blueprint approval or is changed in review |
| **Superseded by ADR-NNN** | Replaced; kept for history |

## Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-core-symfony-modular-monolith.md) | Core backend: Symfony as a domain-driven modular monolith | Accepted |
| [ADR-002](ADR-002-delivery-service-in-go.md) | Separate Go service `delivery` for streams, presence and push | Accepted |
| [ADR-003](ADR-003-backoffice-laravel-separate-context.md) | Back office on Laravel + Filament as a separate bounded context | Accepted |
| [ADR-004](ADR-004-outbox-and-listen-notify.md) | Events through a transactional outbox and PostgreSQL LISTEN/NOTIFY, no broker | Accepted |
| [ADR-005](ADR-005-stream-tickets.md) | Stream authorization with short-lived tickets carrying entitlements | Proposed |
| [ADR-006](ADR-006-realtime-sse-with-polling-fallback.md) | Realtime transport: SSE with a polling fallback | Accepted |
| [ADR-007](ADR-007-postgresql-postgis-storage.md) | PostgreSQL + PostGIS: current position and an unpartitioned history table (revised in the blueprint review) | Accepted |
| [ADR-008](ADR-008-server-computed-time-and-freshness.md) | Server-computed position time and freshness | Accepted |
| [ADR-009](ADR-009-contract-first-openapi.md) | Contract-first REST API with OpenAPI 3.1 | Accepted |
| [ADR-010](ADR-010-frankenphp-runtime-and-edge-proxy.md) | FrankenPHP as PHP runtime and edge proxy | Accepted |
| [ADR-011](ADR-011-android-stack-and-modules.md) | Android stack and module structure | Accepted (stack), Proposed (modules) |
| [ADR-012](ADR-012-always-on-location-foreground-service.md) | One always-on foreground service of type `location` as the only collector | Proposed |
| [ADR-013](ADR-013-places-evaluated-on-server.md) | Places are evaluated on the server | Accepted |
| [ADR-014](ADR-014-push-as-wake-channel.md) | Push as a wake channel and alert transport behind an interface | Proposed |
| [ADR-015](ADR-015-authentication-and-sessions.md) | Authentication with Google ID tokens and own session tokens | Proposed |
| [ADR-016](ADR-016-operator-capabilities-flavor-and-switch.md) | Operator capabilities behind a build flavor and a server switch | Accepted |
| [ADR-017](ADR-017-data-protection-without-e2e.md) | Data protection: encryption in transit and at rest, no end-to-end encryption | Accepted |
| [ADR-018](ADR-018-maps-maplibre-openfreemap.md) | Maps: MapLibre with OpenFreeMap and a server-provided style URL | Accepted |
| [ADR-019](ADR-019-hosting-oracle-always-free.md) | Hosting: local stack until the beta, then one Oracle Cloud Always Free ARM VM in the EU | Accepted |
| [ADR-020](ADR-020-database-image-postgis-multiarch.md) | Own multi-architecture PostGIS image based on the official PostgreSQL image | Accepted |
| [ADR-021](ADR-021-ci-github-actions.md) | Continuous integration on GitHub Actions with path filters and images in GHCR | Proposed |
| [ADR-022](ADR-022-backups-encrypted-two-locations.md) | Nightly encrypted logical backups in two locations with a monthly restore test | Proposed |
| [ADR-023](ADR-023-error-tracking-sentry-with-scrubbing.md) | Error tracking: Sentry Developer plan with mandatory scrubbing, Bugsink as the exit | Accepted |
| [ADR-024](ADR-024-test-database-compose-profile.md) | Test database: a `test` profile in Docker Compose instead of Testcontainers | Accepted |
| [ADR-025](ADR-025-developer-commands-powershell-scripts.md) | Developer commands: PowerShell scripts shared by the developer PC and CI | Proposed |
| [ADR-026](ADR-026-go-test-reports-gotestsum.md) | Go test reports: gotestsum in the `delivery` test image | Proposed |

ADR-019 to ADR-025 record the operations and quality decisions; the procedures themselves are in [docs/ops](../../ops/) and [docs/quality](../../quality/).

## Template

```markdown
# ADR-NNN: Title

- Status: Proposed | Accepted | Superseded by ADR-NNN
- Date: YYYY-MM-DD

## Context
The problem and the forces at play. Facts with sources.

## Options
1. Option — main advantages and drawbacks.

## Decision
What we do.

## Consequences
What becomes easier, what becomes harder, what must be watched, when to revisit.
```

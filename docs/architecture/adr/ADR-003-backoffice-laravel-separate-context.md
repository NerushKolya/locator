# ADR-003: Back office on Laravel + Filament as a separate bounded context

- Status: Accepted
- Date: 2026-11-15

## Context

The operator needs a web tool: a live map during the beta, device diagnostics, and an editor for the remote configuration of the location engine. The owner wants to keep both Symfony and Laravel skills current, with Symfony as the main framework. Filament provides a rich admin interface quickly, but it is built on Eloquent, and two frameworks mapping the same tables would duplicate the domain rules.

## Options

1. **Laravel + Filament with its own data, core data only through an admin API.** Clean ownership; every operator action passes the core's rules and access log. Pages over core data must be custom pages with an API client, not standard CRUD resources.
2. **Laravel + Filament directly over the core's tables.** Least code. Two owners of the same data; consent and visibility rules bypassed or duplicated.
3. **Admin inside the Symfony core (EasyAdmin).** One framework, one process. Weaker admin tooling; no Laravel practice.
4. **Single-page application in TypeScript.** Best TypeScript practice. Much more work than an admin framework.

## Decision

Option 1. `backoffice` is a separate container with its own PostgreSQL schema (`backoffice`) and a database role that has no rights on the core's schema. It owns operator accounts, configuration drafts and publication history. It reads and acts on domain data only through the core's admin API. The operator live map is a Filament page with a TypeScript module (MapLibre GL JS) that uses the same stream as the app, with an operator ticket. The back office is built when the family beta needs it; until then the same operations are console commands in the core.

## Consequences

- One more container (about 100–200 MB of memory, estimate).
- Operator capabilities cannot bypass the operator switch or the access log ([ADR-016](ADR-016-operator-capabilities-flavor-and-switch.md)).
- More code than plain Filament resources, in exchange for intact boundaries.
- The admin API becomes a second contract to maintain; it gets its own contract file when the back office is built.
- TypeScript enters the project where it is natural: the web map and the contract tooling.

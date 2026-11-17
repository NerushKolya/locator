# ADR-001: Core backend — Symfony as a domain-driven modular monolith

- Status: Accepted
- Date: 2026-11-15

## Context

The backend must enforce the product's hardest rules: consent, the group boundary and reciprocity. The project owner has twelve years of PHP experience (Symfony and Laravel), so code review is strongest there. The load is small: about two requests per second at peak for 100 people. The owner wants domain-driven design and room for growth, but only one developer maintains the system and the infrastructure budget is zero.

## Options

1. **Symfony modular monolith.** Doctrine's data mapper keeps entities free of framework code; explicit dependency injection; Messenger can queue in PostgreSQL; long-term-support releases. More verbose than Laravel.
2. **Laravel monolith.** Fastest start, rich admin tooling. Active-record models tie the domain to tables; facades hide dependencies.
3. **NestJS (TypeScript) core.** The most demanded language on the market; structure similar to Symfony. The most critical rules would be written and reviewed in the framework the owner knows least.
4. **Microservices per bounded context.** Independent deployment. Network calls, distributed transactions and several databases for a one-person project with tiny load.
5. **Backend-as-a-service (Firebase, Supabase).** No server code. Authorization lives in security rules that are hard to test; strong lock-in; no learning value for the owner.

## Decision

The core is one Symfony application on PHP 8.5, structured as a modular monolith: modules `Identity`, `Groups`, `Sharing`, `Locations`, `Places`, `Alerts`, `Admin` and a shared `Platform` kernel. Each module has `Domain`, `Application`, `Infrastructure` and `Api` layers and talks to others only through published interfaces and domain events. The same code base runs as `core` (HTTP) and `core-worker` (queue consumers and scheduler). The Symfony branch is the current stable 8.x at project creation, upgraded with each minor release.

## Consequences

- The critical rules live in the language and framework the owner knows best.
- One deployable and one transaction boundary: no distributed consistency problems inside the domain.
- Module boundaries must be enforced by an automated dependency check, otherwise the monolith erodes.
- A module can later be extracted into a service through a new ADR, because it already has an explicit interface and its own tables.
- PHP is unsuitable for long-lived connections, which motivates [ADR-002](ADR-002-delivery-service-in-go.md).

# ADR-002: Separate Go service `delivery` for streams, presence and push

- Status: Accepted
- Date: 2026-11-15

## Context

Map viewers need position changes within seconds ([ADR-006](ADR-006-realtime-sse-with-polling-fallback.md)). That means many long-lived connections, which a request-per-worker PHP runtime handles poorly. The server must also know whether a group's map is being watched (to grant active-view leases) and must send push messages. Firebase publishes an official Admin SDK for Go but not for PHP ([Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)). The owner wants to grow Go skills on a task where Go is objectively the better tool; new services are allowed only with a technical reason.

## Options

1. **Go service `delivery`.** Goroutines make long-lived connections cheap; one small static binary (tens of megabytes of memory); official FCM SDK. A second language on the server and a second place where authorization could leak.
2. **Mercure hub built into FrankenPHP.** Almost no code, same SSE transport, JWT authorization. No viewer-presence logic out of the box; hub licensed under AGPL; pushes would still be sent from PHP through a community library.
3. **Everything in PHP (worker mode or a WebSocket server).** One language. Fights the runtime; fewer learning goals met.
4. **Node.js service.** Official FCM SDK too. Go has the stronger concurrency model and is the owner's stated learning target for services.

## Decision

A separate service `delivery`, written in Go with the standard library, a PostgreSQL driver and the Firebase Admin SDK. It does three things: serves SSE streams to viewers, reports viewer presence per group to the core, and sends the push messages the core has prepared. It contains **no business rules**: it never decides who may see what ([ADR-005](ADR-005-stream-tickets.md)) and receives its work through the outbox ([ADR-004](ADR-004-outbox-and-listen-notify.md)).

## Consequences

- Each language does what it is best at; the Go service has a real reason to exist.
- Two languages to build, test and keep updated from day one.
- The authorization test matrix must also run against `delivery`.
- If `delivery` is down the product degrades instead of failing: the map polls, pushes wait in the outbox.
- Exit path: Mercure in FrankenPHP can take over streaming with little client impact; push sending would then move to the core.

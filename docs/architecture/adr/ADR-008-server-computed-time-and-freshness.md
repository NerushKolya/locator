# ADR-008: Server-computed position time and freshness

- Status: Accepted
- Date: 2026-11-15

## Context

The product promises never to present an old position as current (principle P5). A phone's wall clock can be wrong: set by hand, wrong time zone, drift. Points may arrive late from the offline buffer. Silence has different meanings: fifteen quiet minutes are normal for a stationary phone and a problem for a moving one.

## Options

1. **Trust the phone's timestamps.** Simple. One wrong clock makes a position look fresh or ancient.
2. **Use the server's receive time as the position time.** Immune to clock errors. Wrong for buffered points, which may be hours old.
3. **Server time minus the point's age measured on the phone's monotonic clock.** Immune to wall-clock errors and correct for buffered points; the only error is network delay.

For freshness: fixed thresholds, or thresholds relative to what the phone announced.

## Decision

- Android gives every location a monotonic timestamp. When sending, the phone computes each point's age on the monotonic clock. The server computes `fixedAt = receivedAt − ageMs` (BR-06).
- Points that survived a reboot lose the monotonic reference: they are sent with `timeSource = wall_clock`, and the server uses the device time corrected by the difference between the phone's send time and the server's receive time when that difference exceeds two minutes.
- The phone's wall-clock time is stored alongside for diagnostics and export.
- Every device report announces `nextReportInS`, the interval after which the phone promises to report again. The server computes freshness from it (BR-08): `live` (position age ≤ 2 min), `recent` (last contact within 1.5 × the interval), `stale` (1.5–4 ×), `offline` (beyond 4 ×). Thresholds are configuration.
- The API always exposes both clocks: position time and last contact. Clients never compute freshness categories themselves.

## Consequences

- Correct ordering and ages regardless of phone clocks; BR-09 can rely on `fixedAt`.
- A re-sent point gets a slightly different `fixedAt`, which is why idempotency uses the point id, not the time ([ADR-007](ADR-007-postgresql-postgis-storage.md)).
- Freshness adapts automatically to engine modes and profiles; no per-mode constants on the server.
- The server cannot know why a phone is silent; statuses are worded neutrally unless the phone reported a reason.
- A test with a shifted phone clock is part of the device test matrix.

# ADR-005: Stream authorization with short-lived tickets carrying entitlements

- Status: Proposed
- Date: 2026-11-15

## Context

`delivery` serves event streams but must not decide who may see what ([ADR-002](ADR-002-delivery-service-in-go.md)). The group boundary and reciprocity (BR-01, BR-02) are decided by one policy in the core. Access can change at any moment: a member is removed, a viewer pauses, a session is revoked. A long-lived stream must not outlive the right to receive it.

## Options

1. **`delivery` queries the core's tables.** Always current. Duplicates the rules in Go and gives a second service access to all domain data.
2. **`delivery` calls the core for every event.** Central rules. A request per event and per viewer; tight coupling.
3. **Signed ticket with entitlements + revocation events.** The core signs what the viewer may receive; `delivery` only verifies a signature and matches fields; changes of access are pushed as control events.
4. **Reuse the access token for the stream.** No extra call. `delivery` would need to understand sessions and visibility; the token says nothing about groups.

## Decision

Option 3.

- The app asks the core for a **stream ticket** for the group on screen. The ticket is a JWT signed with a key used only for tickets; `delivery` holds only the public key. It is valid for 60 seconds, usable once, and carries the user id, session id and, per group, the entitlement classes: `status` (member events), `position` (only if the viewer shares) and `sos` (always for members).
- `delivery` forwards an outbox event to a connection only if the event's group and class are in the ticket.
- When anyone's access changes, the core emits a `control` event; `delivery` closes that user's streams with a `reconnect` event, and the app must obtain a new ticket.
- Every stream is closed after a maximum lifetime of 15 minutes *(config)*, which bounds the effect of any missed revocation.
- The ticket travels in the `Authorization` header, not in the URL.

## Consequences

- `delivery` needs no access to domain tables and contains no rules; a compromise exposes only live events of that period.
- Revocation is fast (seconds) but not atomic with the commit; the bound is the stream lifetime. The REST API remains strictly consistent.
- One extra request before opening a stream.
- The operator stream uses the same mechanism with an operator ticket issued only in operator mode.
- Tests must cover expired, replayed and forged tickets and entitlement mismatches.

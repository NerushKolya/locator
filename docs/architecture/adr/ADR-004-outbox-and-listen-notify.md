# ADR-004: Events through a transactional outbox and PostgreSQL LISTEN/NOTIFY, no broker

- Status: Accepted
- Date: 2026-11-15

## Context

The core must tell `delivery` about position changes, member status changes, access revocations and pushes to send. An event must never be lost and must never be visible before the data it describes is committed. The project runs on one small host with a zero budget; every extra moving part costs memory and attention.

## Options

1. **Transactional outbox table + `NOTIFY`.** The event row is written in the same transaction as the state change; `NOTIFY` wakes the consumer; the table is the durable log if a notification is missed. No new infrastructure. One consumer instance is natural; several need coordination.
2. **Message broker (NATS, RabbitMQ, Kafka).** Built for fan-out and many consumers. A new container, new failure modes, and still needs an outbox to be transactional.
3. **Redis pub/sub or streams.** Fast. Another server; pub/sub alone loses messages.
4. **Direct HTTP call from the core to `delivery` after commit.** Simple. Lost when `delivery` is down or the process dies between commit and call.

## Decision

Option 1. The core writes rows to `core.outbox` (channel `stream`, `control` or `push`, audience, class, payload) and issues `NOTIFY` in the same transaction. `delivery` holds a `LISTEN` connection, reads new rows in id order, also polls every few seconds as a safety net, and stores its position in `delivery.relay_cursor`. Delivery is at-least-once; consumers deduplicate by event id. Rows are deleted after 24 hours.

Inside the core, asynchronous reactions use Symfony Messenger with the PostgreSQL transport, also written in the same transaction.

## Consequences

- No broker and no Redis; PostgreSQL is the only stateful component.
- Events are exactly as durable as the data.
- Throughput is bounded by one database and one relay; ample for thousands of users, to be measured before scaling.
- `NOTIFY` payloads are small (the row id), so payload size limits do not matter.
- When more than one `delivery` instance must receive every event, a broker replaces the relay through a new ADR; the outbox table stays as the transactional source.

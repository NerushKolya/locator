# ADR-007: PostgreSQL + PostGIS — current position and an unpartitioned history table

- Status: Accepted
- Date: 2026-11-15; revised 2026-11-17 in the blueprint review (daily partitioning and the receipts table removed from the MVP)

## Context

The system stores a live marker per person, a history of points with a retention period (30 days by default), places as circles, and must accept re-sent points without duplicates. Expected volume is about 300 points per person per day; 100 people for 30 days is under a million rows. Distances must be in metres. The server-computed position time of a re-sent point differs slightly between attempts ([ADR-008](ADR-008-server-computed-time-and-freshness.md)), so idempotency must rely on the point id, not on the time. PostgreSQL requires a unique constraint on a partitioned table to include the partition key.

## Options

1. **PostgreSQL + PostGIS, a small `current_positions` table and one ordinary history table** with a unique key on the point id. Simple; retention is a batched delete.
2. **Same, but the history table partitioned by day**, plus a separate receipts table for idempotency (the unique key cannot be enforced on the partitioned table without the time). Instant retention by dropping partitions; costs a partition-maintenance job, a default-partition alarm and a second insert per point. The first version of this ADR chose it; the blueprint review found it premature at this volume.
3. **One points table, latest row by query.** The map's hottest query scans history.
4. **TimescaleDB.** Automatic partitioning and retention. Part of its features is under a non-open-source licence; unnecessary at this volume.
5. **Redis for the current position.** Fast reads. A second stateful component for a table of a hundred rows.
6. **Non-relational store (BaaS).** No PostGIS, no transactional outbox.

## Decision

Option 1. PostgreSQL 18 with PostGIS 3.6 (official packages only).

- `current_positions`: one row per person, updated by a conditional upsert that applies only if the new position time is later. Late points from the offline buffer never move the marker backwards (BR-09).
- `location_points`: one ordinary table, primary key `(device_id, point_id)`, inserted with `ON CONFLICT DO NOTHING`; index `(user_id, fixed_at desc)`; type `geography(Point, 4326)`. Retention is a daily batched delete.
- Place checks are distance computations against at most 20 circles per group; no spatial index is required yet.
- Each service has its own schema and role; no cross-schema foreign keys.

Full schema: [data-model.md](../data-model.md).

## Consequences

- One database serves data, queue and events; backups and restores cover everything.
- One insert per point and no scheduled partition maintenance.
- Batched deletes leave dead rows that autovacuum reclaims; at this volume this is acceptable.
- Revisit (option 2 or a time-series extension, new ADR) when retention deletes or history queries become measurably slow — expected well beyond 1000 active people, or earlier if the history release needs longer retention.

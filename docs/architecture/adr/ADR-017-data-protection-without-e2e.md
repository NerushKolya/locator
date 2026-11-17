# ADR-017: Data protection — encryption in transit and at rest, no end-to-end encryption

- Status: Accepted (option A, by the owner in the blueprint review, 2026-11-17)
- Date: 2026-11-15

## Context

The owner's requirement: "everything should be encrypted, but it must be possible to obtain data when needed". The server must read positions to evaluate places, send alerts, compute freshness and (in development/beta builds) serve the operator. The threats to stored location data are: interception in transit, theft of backups, theft of the disk or volume, read access to the database through a bug or a secondary service, and full compromise of the host.

## Options

| Option | Protects against | Cost |
|---|---|---|
| **A. Baseline layers:** TLS everywhere; encrypted database volume; backups encrypted with an offline private key; tokens hashed; least-privilege database roles; no coordinates outside the database | Interception, backup theft, disk theft, access through `delivery` or `backoffice` | Low; standard practice |
| **B. A + application-level encryption of coordinates** (key held by the core, outside the database) | Additionally: a database-only leak (dump or SQL injection without code execution) | No PostGIS on encrypted columns: distance checks move into application code; key management and rotation; harder debugging. Does **not** protect against host compromise, because the key is on the same host |
| **C. A + application-level encryption of history only** (points older than a day), current positions in clear | As B for the bulk of sensitive data, while places and the live map keep using PostGIS | Moderate: a re-encryption job, key management; history queries decrypt in the application |
| **D. End-to-end encryption** (keys only on members' phones) | Everything including host compromise and the operator | Server-side places, alerts and freshness on positions become impossible; operator access becomes impossible; key distribution in groups is a project of its own |

## Decision (proposed)

Option **A** for the MVP. Option **C** is reconsidered when the history feature (v1.1) is specified, because history is where the volume and sensitivity are. Option **D** stays a future direction (feature F-55) that needs its own research and a product decision about server-side features.

The requirement "obtainable when needed" is met by A (and by C): the server can read the data, and access is controlled by roles, the operator switch and the access log.

## Consequences

- The privacy policy must say plainly that the service can technically read locations, that backups are encrypted, and how long data is kept.
- Protection against a full host compromise relies on hardening and minimization (retention, immediate deletion), not on cryptography.
- Keys to manage in the MVP: token signing keys and the backup key pair only.
- If the database ever moves to shared or managed hosting, or the user base grows beyond family and friends, option C should be re-evaluated first.

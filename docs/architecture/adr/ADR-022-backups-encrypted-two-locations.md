# ADR-022: Backups — nightly encrypted logical dumps in two locations with a monthly restore test

- Status: Proposed
- Date: 2026-11-17

## Context

PostgreSQL is the only durable state ([overview](../overview.md) §4). The production VM may be reclaimed or lost ([ADR-019](ADR-019-hosting-oracle-always-free.md)). Dumps contain coordinates, so they must be encrypted, and the server must not be able to decrypt them ([ADR-017](ADR-017-data-protection-without-e2e.md)). The data volume is small: about 300 MB of history for 100 people over 30 days ([data model](../data-model.md) §5).

Free storage checked on 2026-11-15: Oracle Object Storage, 20 GB in Always Free ([Oracle](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm)); Backblaze B2, first 10 GB free ([Backblaze pricing](https://www.backblaze.com/cloud-storage/pricing)); Cloudflare R2, 10 GB-month free ([R2 pricing](https://developers.cloudflare.com/r2/pricing/)). Whether B2 or R2 requires a payment card is `not verified`.

## Options

1. **Nightly `pg_dump -Fc`, encrypted with `age` to a public key, uploaded to two locations** (one inside and one outside the hosting provider).
2. **Physical backups with WAL archiving** (pgBackRest). Point-in-time recovery; more moving parts than the data size justifies.
3. **Provider volume snapshots only.** Same provider as the failure domain; not encrypted with our key.

## Decision

Option 1:

- A `backup` container runs nightly: `pg_dump -Fc` → `age` encryption with the owner's public key → upload to Oracle Object Storage and to one external store (Backblaze B2 or Cloudflare R2, chosen at beta setup).
- Retention: 7 daily + 4 weekly dumps.
- A dump is also taken before every database migration in production.
- The private key is kept offline by the owner; it never touches the server.
- A cron monitor alerts when a nightly backup did not report success.
- Once a month a dump is restored into a clean local environment and the backend test suite runs against it. **A backup that was never restored does not count.**

## Consequences

- Recovery point objective: 24 hours; recovery time objective: about 4 hours, including creating a new VM ([nfr.md](../../quality/nfr.md)).
- Positions remain in backups for up to 4 weeks after deletion in the database; the privacy policy says so.
- Revisit (option 2) if the data grows beyond a few GB or a 24-hour loss window becomes unacceptable.

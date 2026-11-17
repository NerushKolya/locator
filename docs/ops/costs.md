# Costs

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [deployment](deployment.md) · [ADR-019](../architecture/adr/ADR-019-hosting-oracle-always-free.md) · [data model §5](../architecture/data-model.md) · [NFR](../quality/nfr.md)

Prices and limits were checked on 2026-11-15 against the official pages linked in the ADRs; they change, so re-check before relying on them. Load figures are estimates until the spike and the beta measure them.

## 1. Development

| Item | Cost |
|---|---|
| Android Studio, SDK, emulator, Docker Desktop (personal use) | $0 |
| GitHub repository and Actions (public repository) | $0 |
| Tailscale (personal plan) for outdoor tests | $0 |
| Test phones | Already owned |
| **Total** | **$0** |

## 2. One-off costs

| Item | Cost | When |
|---|---|---|
| Google Play developer account | $25 once ([Play Console sign-up](https://support.google.com/googleplay/android-developer/answer/6112435)) | Before the closed test for the store release |
| Domain | $0 (deSEC subdomain; `eu.org` if approved) | Before the beta |

## 3. Running costs by number of users

Model: a typical day ([NFR](../quality/nfr.md)) per person ≈ 300 stored points and ≈ 500 device reports; 30-day retention.

| | 10 users (family) | 100 users (family and friends) | 1000 users |
|---|---|---|---|
| Device reports per day | ≈ 5 000 | ≈ 50 000 | ≈ 500 000 |
| Peak device reports per second | < 1 | ≈ 2 | ≈ 20 |
| Stored history (30 days, with indexes) | ≈ 30 MB | ≈ 300 MB | ≈ 3 GB |
| Nightly compressed dump | < 20 MB | ≈ 100 MB | ≈ 1 GB |
| Backup storage (7 daily + 4 weekly) | < 0.3 GB | ≈ 1.1 GB | ≈ 11 GB — exceeds the external free tier of 10 GB |
| Server outbound traffic per month (API + streams) | < 1 GB | ≈ 5–10 GB | ≈ 50–100 GB |
| Server memory | ≈ 1.2 GB | ≈ 1.5 GB | ≈ 2–3 GB |
| Hosting | $0 (A1 free tier) | $0 | $0 on A1 if within 2 OCPU / 12 GB; external backup storage may cost a few dollars per month |
| Error tracking | $0 | $0 | $0 while under 5 000 errors per month |
| Push (FCM) | $0 | $0 | $0 |
| Map tiles | $0 (OpenFreeMap; phones fetch tiles directly) | $0 | $0; no service-level agreement — plan B is self-hosted tiles |
| **Total per month** | **$0** | **$0** | **$0–5** *(estimate)* |

Without the free tier (plan C, a paid VPS) the stack fits a 2 vCPU / 4 GB VPS at about €6 per month for any of the three sizes.

## 4. How update frequency drives load

| Change | Effect |
|---|---|
| Moving interval 60 s → 30 s | Moving reports and stored points roughly double (moving time is ≈ 2.5 h of 24 h, so total reports grow by ≈ 40%); battery cost on the phone grows more than server cost |
| Stationary heartbeat 15 min → 5 min | Heartbeats triple (≈ 84 → 250 per day); history size grows ≈ 30%; battery cost at night grows noticeably |
| More map viewing (active view) | Each viewing minute adds 6 points per viewed member; dominated by family habits, measured in the beta |
| Retention 30 → 90 days | History storage and backup size triple; no effect on request load |

The server is never the bottleneck at these sizes; the phone battery is. Frequencies are remote configuration *(config)* and are tuned on battery measurements, not on server capacity.

## 5. Triggers that would cost money

| Trigger | Response |
|---|---|
| Oracle reclaims or changes the free VM | Plan B (home PC) or plan C (paid VPS) by the owner's decision |
| Backups exceed the external free tier | Shorter weekly retention or paid storage (cents per GB) |
| Error quota regularly exceeded | Fix the noisy error, then self-hosted Bugsink |
| Tile provider limits or disappears | Self-hosted PMTiles on object storage |

# ADR-019: Hosting — local stack until the beta, then one Oracle Cloud Always Free ARM VM in the EU

- Status: Accepted
- Date: 2026-11-17

## Context

The server side is five containers needing about 1.2 GB of memory ([overview](../overview.md) §4). It must run 24/7 without sleeping: a sleeping server breaks SSE streams and keeps the sharing phone's radio awake longer. The budget is $0, servers must be in the EU, and the architecture must stay portable (stateless containers, configuration from the environment, no provider-specific services).

Facts checked on 2026-11-15:

- Oracle Cloud Always Free includes Ampere A1 compute (currently 2 OCPU and 12 GB memory in total), 200 GB block storage, 20 GB object storage and 10 TB outbound traffic per month, without an end date. Idle instances may be reclaimed when CPU, network and memory utilization stay below 20% over 7 days ([Oracle Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm)).
- AWS Free plan lasts 6 months; Google Cloud free VM has 1 GB memory and US regions only; Azure free services last 12 months; free PaaS tiers sleep or delete free databases.
- Tailscale Funnel exposes a local service on a stable `*.ts.net` HTTPS address for free ([Tailscale Funnel](https://tailscale.com/kb/1223/funnel)); Cloudflare quick tunnels do not support server-sent events ([Cloudflare docs](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)).

## Options

1. **Local stack + tunnel until the beta, then Oracle A1 (EU).** $0, enough resources; reclamation and policy-change risks; ARM architecture.
2. **Paid VPS from the start** (for example Hetzner, about €6/month). Predictable; violates the $0 budget.
3. **Free PaaS + managed database.** Sleeping services and database limits break the realtime design.
4. **Home PC as the server.** $0; depends on home power, network and the PC being on.

## Decision

- Development and the location spike run on the developer PC in Docker Compose. Outdoor tests reach the local stack through Tailscale Funnel.
- By the family beta the stack moves to one Oracle Cloud Always Free A1 VM in an EU region, Docker Compose, automatic HTTPS in the edge proxy, SSH only over Tailscale.
- Plan B: home PC + Funnel; plan C: a paid VPS only by the owner's decision.

## Consequences

- No hosting cost until the beta; the location spike does not wait for a server.
- Images must be built for `arm64` (see [ADR-020](ADR-020-database-image-postgis-multiarch.md)).
- Reclamation is mitigated by realistic memory sizing and by nightly off-site backups plus infrastructure-as-code, so moving to another host is a matter of hours ([ADR-022](ADR-022-backups-encrypted-two-locations.md)).
- Registration may require a payment card (`not verified`); this is decided by the owner before the beta.
- Revisit when Oracle changes the free tier, when the VM is reclaimed, or when measured load exceeds the VM.

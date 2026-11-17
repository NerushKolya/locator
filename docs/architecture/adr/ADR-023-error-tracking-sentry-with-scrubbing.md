# ADR-023: Error tracking — Sentry Developer plan with mandatory scrubbing, self-hosted Bugsink as the exit

- Status: Accepted
- Date: 2026-11-17

## Context

Crashes and server errors must be visible to the owner across the app, the core, `delivery` and the back office. Error reports must never contain coordinates, place names, tokens, e-mail addresses or invitation codes (principle P6, [backend](../backend.md) §10). Budget is $0 and no third-party analytics may be added.

Facts checked on 2026-11-15: the Sentry Developer plan is free for one user, 5 000 errors per month, 30-day retention, one uptime monitor and one cron monitor ([Sentry pricing](https://sentry.io/pricing/)). Bugsink is a self-hosted tracker that accepts the Sentry SDK protocol ([Bugsink](https://www.bugsink.com/)); its license terms are `not verified`. Firebase Crashlytics would add another Google SDK to declare in Data safety.

## Options

1. **Sentry SaaS (Developer)** with one SDK family for PHP, Go, Android and the browser; scrubbing in each SDK.
2. **Self-hosted Bugsink or GlitchTip** from the start. Data stays with us; one more container and its maintenance.
3. **Crashlytics** for Android and something else for the server. Two tools, extra Data safety entry.

## Decision

Sentry Developer for all services and the app. Every SDK gets a `beforeSend` scrubbing filter that drops coordinates, place names, tokens, e-mail addresses and invitation codes, and each filter has an automated test (security acceptance criterion 4). The DSN is configuration, so moving to self-hosted Bugsink means changing the DSN, without code changes. Crashlytics is not used. The single uptime monitor watches the public `/health`; the cron monitor watches the nightly backup ([ADR-022](ADR-022-backups-encrypted-two-locations.md)).

## Consequences

- Error data is stored by a third party in the United States; the privacy policy lists it as a processor.
- A storm of errors can exhaust the monthly quota; client-side sampling and rate limits are configured.
- Revisit when the quota is regularly exceeded or when data residency becomes a requirement.

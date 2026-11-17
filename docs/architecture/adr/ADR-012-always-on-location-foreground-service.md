# ADR-012: One always-on foreground service of type `location` as the only collector

- Status: Proposed
- Date: 2026-11-15

## Context

Android gives a background app location only "a few times each hour" ([background location limits](https://developer.android.com/about/versions/oreo/background-location-limits)). Starting a foreground service from the background is prohibited on Android 12+ except for listed cases such as a high-priority push, boot completed, or a geofence event ([restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)); a location service started from the background gets location only if the app holds the background-location permission ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)). Periodic WorkManager jobs run at most every 15 minutes and not in Doze. Phone makers kill background apps beyond what Android does. Google Play accepts user-initiated location sharing as a use of the `location` service type ([declaration](https://support.google.com/googleplay/android-developer/answer/13392821)).

## Options

1. **Always-on foreground service while sharing is active**, changing modes internally.
2. **Start a service on demand** (by push or geofence) and stop it afterwards. Lowest idle cost; depends on push delivery and background-start exceptions for every single point.
3. **WorkManager periodic jobs.** No persistent notification. Fifteen-minute floor, no work in Doze, unreliable location in the background.
4. **Geofences and passive updates only.** Cheapest. Coarse and delayed; no control over freshness.

## Decision

Option 1. `LocationService` (type `location`) runs for as long as the person shares and is the only component that requests location. A mode automaton inside it adapts the request: no active request while stationary (passive updates, an anchor geofence and a control fix at the heartbeat), high accuracy while moving, faster for active view and SOS. The background-location permission is required for full function (restart after reboot, by push, by geofence); without it the app works in a declared limited mode. Uploads are done by the service itself; WorkManager is only a watchdog. The persistent notification is never hidden and always offers "Pause".

## Consequences

- The most reliable legal design; it matches the product's "nothing hidden" principle because sharing is always visibly on.
- A permanent notification is part of the product and must be useful (state, who is viewing, pause).
- Battery depends on the stationary mode really being idle; this is the main subject of the spike.
- Phone-maker restrictions remain the top risk; mitigations are user instructions, the self-check and several recovery layers, not code tricks.
- Requires the background-location declaration and the foreground-service declaration for Google Play.
- The "only on request" mode of the store release reuses the same service with a different mode.

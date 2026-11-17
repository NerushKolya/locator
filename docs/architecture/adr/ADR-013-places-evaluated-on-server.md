# ADR-013: Places are evaluated on the server

- Status: Accepted
- Date: 2026-11-15

## Context

Members subscribe to arrival and departure alerts for a group's places (US-040–US-043). Android's Geofencing API allows at most 100 geofences per app, needs background location, reacts within minutes and may not fire without Wi-Fi or network; registrations are lost on reboot ([geofencing](https://developer.android.com/develop/sensors-and-location/location/geofencing)). The server already receives every point and already sends notifications to other members.

## Options

1. **On the device** with the Geofencing API. Works even when points are not being uploaded. Limits, delays, dependence on phone makers; every place change must be synchronized to all phones.
2. **On the server**, from received points. No limits, immediate effect of place edits, one implementation, testable. Detects nothing while the member's points do not arrive; delay equals the upload interval.
3. **Hybrid:** server as the base, device geofences as an accelerator.

## Decision

Option 2. The core worker evaluates place transitions for each accepted batch: distance of each point to the group's places, with hysteresis that ignores inaccurate points and confirms a change by a second point or a dwell time ([data-model.md §3.5](../data-model.md)). Alerts go only to subscribers who share and whom the visibility policy allows. On the device the Geofencing API is used only for the engine's stationary anchor ([ADR-012](ADR-012-always-on-location-foreground-service.md)).

## Consequences

- Places are cheap enough to be in the MVP and share the data path of the map.
- Alert delay is about one upload interval while moving (around a minute); honest wording shows the estimated crossing time.
- No alerts for a member who is paused or offline — consistent with reciprocity and stated in the product.
- Hysteresis parameters need tuning with real data after the spike.
- Option 3 stays possible later for a person's own places, through a new ADR.

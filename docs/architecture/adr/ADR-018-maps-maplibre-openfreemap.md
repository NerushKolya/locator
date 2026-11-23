# ADR-018: Maps — MapLibre with OpenFreeMap and a server-provided style URL

- Status: Accepted (map quality and Compose integration to be confirmed on a prototype)
- Date: 2026-11-15

## Context

The app shows members on a map; the back office shows a live map in the browser. The budget is zero and no payment card should be required. No vendor from the Russian Federation may be used. The public OpenStreetMap tile servers forbid heavy app use ([tile usage policy](https://operations.osmfoundation.org/policies/tiles/)). A free tile service may disappear, so the provider must be replaceable without an app release.

## Options

1. **MapLibre Native + OpenFreeMap.** Free, no key, no registration, commercial use allowed, no usage limits; no service-level agreement ([OpenFreeMap](https://openfreemap.org/)). Same style in the browser with MapLibre GL JS.
2. **MapLibre + self-hosted tiles** (PMTiles on static storage, or a self-hosted OpenFreeMap). Independent. Storage and setup work.
3. **Google Maps SDK.** Excellent data; SDK usage is free, but a Google Cloud project with billing enabled and an API key are required ([usage and billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing)). Proprietary.
4. **Mapbox, MapTiler, Stadia.** Keys, accounts, user-based or non-commercial free tiers.

## Decision

MapLibre Native on Android and MapLibre GL JS in the back office, with OpenFreeMap as the tile and style provider. The style URL is part of the remote configuration served by the core, not a constant in the app, so the provider can be switched without a release. Plan B is option 2. Geocoding is not used: places are named by people.

## Consequences

- No cost, no key, no billing account.
- Tile requests go from the phone directly to the tile provider, which therefore sees the phone's IP address and the map area being viewed — not user identities or members' coordinates. This is listed in the [data inventory](../../privacy/data-inventory.md) and the privacy policy.
- Attribution to OpenStreetMap must stay visible.
- The map must degrade gracefully when tiles fail: the member list with statuses keeps working (US-030 AC3).
- Map quality for Ukraine and the Compose integration are checked on the walking-skeleton prototype; if unsatisfactory, the decision is revisited (option 3 or 2).
- Walking-skeleton prototype: MapLibre Compose 0.20.0 (`org.maplibre.compose`) is kept. It takes the style URL at run time, shows the OpenFreeMap and OpenStreetMap attribution and the logo by default, and reports style-load failures (`MapEvent.StyleLoadFailed`), which the screen turns into the "map unavailable" placeholder. It builds with the project's Compose BOM and AGP 9; it needs `compileSdk` 37.1 and a runtime artifact (`maplibre-compose-runtime-opengl-android`), without which the app fails at start on a missing native library. Size: the native libraries add about 14 MB per ABI (uncompressed); the unsigned universal `store` release APK with R8 is 42.8 MB, so release builds should be split by ABI before publication (stage 12). Map quality for Ukraine is judged by the owner in the demo item.

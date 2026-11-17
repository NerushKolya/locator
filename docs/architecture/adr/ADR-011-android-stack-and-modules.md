# ADR-011: Android stack and module structure

- Status: Accepted (stack) · Proposed (module structure)
- Date: 2026-11-15

## Context

The app is native Android (API 29+, target 36), built and tested from the command line by a developer new to Android. Errors should surface at build time rather than on the phone. The hardest part, background location, must be isolated and testable on the JVM. The app needs an SSE client and an offline buffer.

## Options

- **UI:** Jetpack Compose vs the View system (legacy path).
- **Dependency injection:** Hilt (compile-time graph validation) vs Koin (simpler, errors at run time).
- **HTTP:** Ktor Client (Kotlin-first, SSE plugin, kotlinx.serialization) vs Retrofit + OkHttp (most common, SSE through a separate artifact).
- **Cross-platform:** Kotlin Multiplatform or Flutter vs native. iOS is not planned for the first releases, and background location is platform-specific.
- **Structure:** a single module vs many small modules vs a moderate split.

## Decision

- Kotlin, Jetpack Compose with Material 3, Coroutines and Flow, **Hilt**, **Ktor Client** on the OkHttp engine with kotlinx.serialization, Room, DataStore, WorkManager, Fused Location Provider, Credential Manager, Firebase Cloud Messaging, MapLibre Native.
- Gradle with Kotlin DSL and a version catalog; exact versions are fixed when the project is created.
- Unidirectional data flow: immutable UI state in a `StateFlow`, events to the view model, repositories as the source of truth.
- A moderate multi-module layout: `app`, `core:*` (model, network, database, datastore, data, location, designsystem, ui, testing) and `feature:*` per user-facing area. Modules appear when their feature is built; the spike starts with four.
- Two distribution flavors, `internal` and `store` ([ADR-016](ADR-016-operator-capabilities-flavor-and-switch.md)).
- No advertising or analytics SDKs.

Details: [android.md](../android.md).

## Consequences

- Dependency-graph mistakes fail the build instead of crashing on the phone.
- The location engine is a separate module with injected clocks and sources, testable without a device.
- More Gradle configuration than a single module; convention plugins keep it manageable.
- The Ktor SSE plugin and the MapLibre–Compose integration are verified in the walking skeleton; OkHttp's SSE client and an `AndroidView` wrapper are the fallbacks.
- iOS, if ever decided, is a separate client of the same contract.

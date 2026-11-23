# Feature Specification: Walking skeleton

**Feature Branch**: `001-walking-skeleton`

**Created**: 2026-11-17

**Status**: In progress

**Input**: Roadmap stage 1 ([roadmap](../../docs/roadmap/roadmap.md)): every part of the system exists, builds, runs and is tested in continuous integration, with no product features yet.

## Clarifications

### Session 2026-11-23

- Q: Must the remote configuration operation (`GET /v1/config`) work without signing in? → A: Yes, permanently public: the operation has no authentication requirement and no `401` response; the API contract is changed in this feature.
- Q: Must the structured log format and log scrubbing (coordinates, tokens, e-mail addresses, invitation codes) with tests already exist in this feature? → A: Yes, in the core service, the delivery service and the app.
- Q: When does a service count as ready on its health check? → A: One health operation per service (as in the API contract). The core service answers "ok" only while the database is reachable and all migrations are applied, otherwise "down" with a not-ready status code; the delivery service answers "ok" as soon as its process runs. Separate liveness and readiness addresses can be added later without a breaking change.
- Q: Which point does the map show by default without location permission, and where is it defined? → A: A constant in the app: the centre of Kyiv (Maidan Nezalezhnosti) at city-level zoom; no contract change.

## User Scenarios & Testing *(mandatory)*

The users of this feature are the developer (project owner) and future contributors. The feature delivers a working path from a fresh clone to a running server stack and an installed app, so every later feature only adds behaviour.

### User Story 1 - Start the server stack with one command (Priority: P1)

A developer with a prepared PC (see [development environment](../../docs/ops/dev-environment.md)) clones the repository, creates the local configuration from the example file and starts the whole server side with one command. The command waits until every service reports healthy and prints where it can be reached.

**Why this priority**: every later stage, including the location spike, needs a running server; without it nothing else can be demonstrated.

**Independent Test**: on a clean clone, run the environment check and the start command; all services report healthy and the remote configuration can be read from the documented address.

**Acceptance Scenarios**:

1. **Given** a PC that passes the environment checks and a fresh clone with local configuration created from the example, **When** the developer runs the start command, **Then** the database, the core service and the delivery service start, database migrations are applied and the command finishes successfully only after every service reports healthy.
2. **Given** a running stack, **When** a client requests the remote configuration, **Then** it receives a response that matches the published API contract, including the map style address and the default location-engine parameters.
3. **Given** a running stack, **When** the developer runs the stop command, **Then** all services stop and the stored database data survives the next start.
4. **Given** the default host port is already used by another program, **When** the developer changes only the project's port in the local configuration, **Then** the stack starts on the new port and nothing else on the PC is affected.

---

### User Story 2 - See the app talk to the server on a phone and on the emulator (Priority: P1)

The developer installs the development build of the app on a test phone over USB and on the emulator with one command. The app shows whether the server is reachable and shows a map of the current area drawn with the map style received from the server.

**Why this priority**: the next stage measures background location on the real phones; the build, install and connection path must already work, and the map provider must be accepted for Ukraine before map work starts.

**Independent Test**: with the stack running, install on one phone and on the emulator; both show "server reachable" and a map of Ukraine with readable streets and labels.

**Acceptance Scenarios**:

1. **Given** a running stack and a phone connected over USB, **When** the developer runs the install command, **Then** the development build is installed, the phone is connected to the local server, and the app shows "server reachable".
2. **Given** a running stack and the emulator, **When** the app is started, **Then** it reaches the server through the emulator's host address and shows "server reachable".
3. **Given** the server is stopped, **When** the app is opened or refreshed, **Then** it shows "server not reachable" with the address it tried, and does not crash.
4. **Given** the remote configuration contains a map style address, **When** the map screen opens, **Then** the map of the current area is drawn with that style and shows the required map data attribution.
5. **Given** both distribution variants (development and store), **When** they are built, **Then** both build successfully, and the store variant has no way to change the server address.

---

### User Story 3 - Checks run automatically on every change (Priority: P2)

When the developer opens a pull request, automated checks run only for the parts of the repository that changed, using the same commands the developer runs locally, and the pull request cannot be merged while a required check fails.

**Why this priority**: keeps `main` always working from the first commit and makes "green locally" equal to "green in CI"; less urgent than a running system, but needed before features start.

**Independent Test**: open a pull request that changes only the API contract; only the contract checks and the related backend checks run and pass; a deliberately broken contract makes the pull request fail.

**Acceptance Scenarios**:

1. **Given** a pull request that changes only Android code, **When** checks run, **Then** only the Android checks and the secret scan run.
2. **Given** a pull request that introduces a breaking change to the API contract, **When** checks run, **Then** the contract check fails and names the breaking change.
3. **Given** a pull request whose checks all pass, **When** the developer looks at it, **Then** it is mergeable; with any required check failing it is not.
4. **Given** any check that runs in CI, **When** the developer runs the matching local command, **Then** the same tests run with the same result.

---

### User Story 4 - Diagnose the environment (Priority: P3)

When something does not work, the developer runs the environment check and gets one line per check with a clear status and a hint, without the check changing anything on the PC.

**Why this priority**: saves time after updates of tools or the operating system; useful but not blocking for the first demo.

**Independent Test**: stop the container engine and run the check; it reports the engine as failed with a hint and exits with a failure code.

**Acceptance Scenarios**:

1. **Given** a correctly prepared PC, **When** the developer runs the environment check, **Then** every automated check from the [environment checklist](../../docs/ops/env-checklist.md) reports OK and the command succeeds.
2. **Given** the container engine is not running, **When** the check runs, **Then** it reports that check as failed with a hint and the command exits with a failure code.
3. **Given** no phone is connected, **When** the check runs, **Then** it reports a warning, not a failure.

---

### Edge Cases

- The PC has an older container engine or no Compose v2: the environment check fails with the required version.
- The database volume holds data from an older schema: migrations bring it to the current schema or fail with a clear message; data is never silently dropped.
- The phone was connected after the stack started: the install command still sets up the port forwarding.
- The map style address in the remote configuration is unreachable: the app shows the connection status and a "map unavailable" placeholder instead of a blank screen.
- The emulator image is API 29 (the minimum supported version): the app installs and shows the same screens.
- Local configuration is missing a value that the example lists: the start command stops before starting services and names the missing value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST contain the server services described in the [architecture overview](../../docs/architecture/overview.md) as runnable placeholders: a core service and a delivery service, each with one health check operation (as defined in the API contract) that contains no data beyond the status. The core service MUST report "ok" only while the database is reachable and all migrations are applied, and "down" with a not-ready status code otherwise; the delivery service has no dependencies in this feature and reports "ok" while its process runs.
- **FR-002**: The core service MUST serve the remote configuration operation exactly as defined in the API contract (`api/openapi.yaml`), with built-in defaults for the map style address and the location-engine parameters. The operation MUST be available without signing in (the app needs it before an account exists, for example for the legal page links during onboarding); the API contract MUST declare it without an authentication requirement and without an "unauthorized" response.
- **FR-003**: Responses of the core service MUST be validated against the API contract in automated tests.
- **FR-004**: The database MUST be built from the project's own image definition that works on both the developer PC architecture and the production server architecture, and MUST include the geographic extension.
- **FR-005**: Database schema changes MUST be applied by versioned migrations, run automatically when the local stack starts.
- **FR-006**: One command MUST start the local stack and return success only after every service reports healthy; one command MUST stop it; stored data MUST survive a restart.
- **FR-007**: The host port of the stack MUST be configurable in the local configuration file; the example file MUST list every variable name without secret values.
- **FR-008**: Developer commands MUST exist for: environment check, start, stop, backend tests, contract checks, Android tests, install on a phone, and reading app logs — as listed in [ADR-025](../../docs/architecture/adr/ADR-025-developer-commands-powershell-scripts.md). Each MUST be non-interactive and return a failure code on failure.
- **FR-009**: The environment check MUST implement the automated checks of the [environment checklist](../../docs/ops/env-checklist.md) and MUST NOT change system settings or install software.
- **FR-010**: Backend tests MUST run against a separate test database started by the test profile, never against the development database.
- **FR-011**: The Android app MUST have the two distribution variants (development/beta and store) and MUST build both; only the development variant MAY let the server address be changed.
- **FR-012**: The app MUST show whether the server is reachable and the address it uses.
- **FR-013**: The app MUST show a map of the current area using the map style address from the remote configuration, with the attribution required by the map data license; if the style cannot be loaded, a placeholder MUST be shown.
- **FR-014**: The install command MUST build the development variant, install it on the connected phone and set up port forwarding to the local stack.
- **FR-015**: Continuous integration MUST run, on pull requests and on pushes to `main`, only the checks whose parts of the repository changed, plus a secret scan on every change, as described in [CI](../../docs/ops/ci.md).
- **FR-016**: Each CI check MUST call the same developer command that runs it locally.
- **FR-017**: The contract check MUST lint the API contract and fail on breaking changes compared with `main`.
- **FR-018**: Memory use of every service of the running stack MUST be measured once and recorded as the baseline for NFR-SRV-06.
- **FR-019**: The app MUST NOT request any location, notification or background permission in this feature.
- **FR-020**: The core service, the delivery service and the app MUST write structured logs (one machine-readable record per event) through a shared scrubbing step that removes or masks coordinates, place names, tokens, e-mail addresses and invitation codes before a record is written. Each of the three MUST have automated tests that log records containing every forbidden kind of value and verify that none of them appears in the output.

### Key Entities

- **Remote configuration**: a versioned set of values the server gives to apps: map style address and location-engine parameter defaults. In this feature only built-in defaults exist; editing comes with the back office.
- **Health status**: per service, one status value ("ok", "degraded" or "down") with no other data; "ok" means the service is ready to serve requests.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Starting from a fresh clone on a prepared PC, the developer reaches a healthy stack and an installed app on a phone by following the documentation only, without undocumented steps.
- **SC-002**: The start command reports a healthy stack within 3 minutes on a warm start (images already present) on the development PC.
- **SC-003**: On both test phones and on emulators with API 29 and the latest API, the app shows "server reachable" within 5 seconds of opening.
- **SC-004**: The owner judges the map of the home city, at street level and city level, as readable with Ukrainian labels; otherwise the map decision is reopened.
- **SC-005**: A pull request with all checks passing completes CI in under 15 minutes; a pull request touching only one part of the repository skips the checks of the other parts.
- **SC-006**: The recorded memory baseline of the stack is under 1 GB with no load.
- **SC-007**: 100% of the automated checks of the environment checklist are implemented in the environment check.

## Assumptions

- The PC is prepared as described in the development environment document (Android Studio, platform tools, container engine).
- The production server, back office, accounts and push are out of scope; the remote configuration has no editor yet.
- The map style provider is the one chosen in [ADR-018](../../docs/architecture/adr/ADR-018-maps-maplibre-openfreemap.md); this feature only confirms it.
- The repository is public, so continuous integration has no minute limits that affect this feature.
- "Current area" on the map means the area around a fixed default point defined as a constant in the app: the centre of Kyiv (Maidan Nezalezhnosti) at city-level zoom (no location permission in this feature, no personal place in the public repository); the developer can pan to any place, including the home city for SC-004.

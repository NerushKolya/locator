# Feature Specification: Location spike

**Feature Branch**: `002-location-spike`

**Created**: 2026-11-17

**Status**: Draft

**Input**: Roadmap stage 2 ([roadmap](../../docs/roadmap/roadmap.md)): prove that background location sharing works on the two test phones and measure freshness, survival and battery against the [non-functional requirements](../../docs/quality/nfr.md) before investing in accounts and groups. Stories: US-021 (minimal), US-024, US-025 (Balanced profile only), US-026 from the [PRD](../../docs/product/prd.md).

## User Scenarios & Testing *(mandatory)*

Actors: the **owner** carrying a test phone (the sharing person) and the **owner as observer** looking at a test page on the PC or another phone. Test phones: P1 (OnePlus 13) and P2 (RedMagic 8S Pro), see the [device test matrix](../../docs/quality/device-test-matrix.md).

### User Story 1 - Share location in the background with a visible notification (Priority: P1)

The owner turns sharing on in the app on a test phone, puts the phone in a pocket and lives normally. While sharing is on, the phone shows a persistent notification that cannot be hidden, and points reach the server with a frequency that adapts to whether the phone is still, walking or in a vehicle. The owner can pause from the notification at any moment.

**Why this priority**: this is the main technical risk of the whole product; if background sharing does not work on these phones, nothing else is worth building.

**Independent Test**: turn sharing on, lock the phone and walk for 30 minutes; the observer sees a continuous track and the age of the newest point stays within the freshness target.

**Acceptance Scenarios**:

1. **Given** sharing is off, **When** the owner turns it on and grants the required permissions, **Then** a persistent notification appears stating that location is being shared, with a Pause action.
2. **Given** sharing is on and the phone is locked in a pocket, **When** the owner walks for 30 minutes, **Then** points arrive on the server about once a minute and the newest point is never older than the moving-freshness target (NFR-LOC-01).
3. **Given** the phone has been still for 5 minutes, **When** it stays still overnight, **Then** it sends a heartbeat with its status at least every 15 minutes and requests no high-accuracy location in between (NFR-LOC-02, NFR-BAT-02).
4. **Given** the phone is still, **When** the owner leaves home, **Then** the phone switches to the walking mode within the detection target (NFR-LOC-03) and to the vehicle mode after three consecutive fast points.
5. **Given** sharing is on, **When** the owner taps Pause in the notification, **Then** collection stops immediately, the foreground service stops, an ordinary quiet notification shows the paused state with a Resume action, and the server receives the paused status once.
6. **Given** sharing is on, **When** the owner turns it off, **Then** collection stops, the notification disappears and no further points are sent.

---

### User Story 2 - Nothing is lost without network (Priority: P1)

The phone keeps working without a connection: points are kept on the phone and delivered later in order, without duplicates, and the newest position is delivered first so the observer sees the current place at once.

**Why this priority**: losing points or showing an old position as current breaks the honesty promise; the buffer and idempotent upload are the base for every later feature.

**Independent Test**: turn off mobile data and Wi-Fi, walk for 2 hours, reconnect; the server gets every point exactly once, and the current position is the newest one.

**Acceptance Scenarios**:

1. **Given** no network, **When** the owner walks for 2 hours, **Then** all accepted points are kept on the phone.
2. **Given** buffered points, **When** the network returns, **Then** the newest point is delivered first, older points follow in batches, and the server stores each point once (NFR-LOC-07).
3. **Given** a batch was delivered but the confirmation was lost, **When** the phone sends the same batch again, **Then** the server accepts it without storing duplicates.
4. **Given** a late point arrives after a newer one, **When** the server stores it, **Then** the current position does not move backwards (NFR-LOC-08).
5. **Given** the phone's clock is wrong by an hour, **When** points are delivered, **Then** the server computes their time from the point age and stores the correct time.
6. **Given** the buffer reaches its limit (7 days or 10 000 points), **When** new points arrive, **Then** the oldest points are dropped first and the drop is recorded in the engine journal.

---

### User Story 3 - Sharing survives the phone's life (Priority: P1)

Sharing continues after the app is swiped away, after a reboot, after an app update and through the night, without the owner opening the app. When the system or the phone maker stops it anyway, the phone tells the owner and the server can see that the phone went silent.

**Why this priority**: survival on real firmware is the part that cannot be simulated; it decides whether the product is viable.

**Independent Test**: reboot the phone and do not open the app; the first point arrives without any interaction; repeat after swiping the app away and after an update.

**Acceptance Scenarios**:

1. **Given** sharing is on, **When** the app is swiped away from recent apps, **Then** sharing continues.
2. **Given** sharing was on, **When** the phone reboots or the app is updated, **Then** sharing restarts without the owner opening the app, and the engine journal records the restart reason.
3. **Given** the service was stopped by the system, **When** the periodic watchdog runs, **Then** it restarts sharing where the system allows it, or shows a notification "Sharing stopped — tap to resume".
4. **Given** the app is force-stopped, **When** time passes, **Then** nothing is sent, and on the next start the app offers to resume.
5. **Given** a phone-maker restriction is configured by the in-app instructions written during this feature, **When** the survival scenarios are repeated, **Then** they pass on both phones (NFR-LOC-06).

---

### User Story 4 - Ask for a fresh location and inspect results (Priority: P2)

The owner, as observer, opens a protected test page, sees each test phone's latest position, track, mode, status and heartbeat statistics, and can ask a phone for a fresh location. A phone with a running service answers by switching to the active-view mode for a short lease and shows a notification that its location was requested.

**Why this priority**: needed to measure request latency (NFR-LOC-04) and to read results; the real map and groups come later.

**Independent Test**: with phone P1's screen off for 5 minutes, request a fresh location from the test page; a new point appears within 30 seconds and P1 shows a request notification.

**Acceptance Scenarios**:

1. **Given** a phone with a running service, **When** the observer requests a fresh location, **Then** the request is delivered in the response to the phone's next report or through the wake channel, the phone shows a notification that its location was requested, and a new point arrives within 30 seconds in at least 90% of attempts.
2. **Given** the active-view lease expires, **When** no one renews it, **Then** the phone returns to the mode that matches its movement.
3. **Given** the observer opens the test page without the owner's secret, **When** the page loads, **Then** no device data is shown.
4. **Given** a device, **When** the observer opens its statistics, **Then** the page shows position age over time, heartbeat intervals, gaps, duplicates (expected zero) and time spent in each mode.

---

### User Story 6 - Wake a stopped phone and learn which background starts work (Priority: P1)

The owner stops sharing on a test phone the way the system or the firmware would, then tries every way the app may legally start sharing again from the background: a push message, the boot and update broadcasts, the stationary geofence, the watchdog job, an alarm and a tap on a notification. The result is a table that says which paths start sharing with location access on each phone, with and without the battery-optimization exemption.

**Why this priority**: fresh-location requests to a stopped phone, revival after a silent stop, the end of a timed pause and the operator's remote turn-on all rely on starting the service from the background. Android allows it only in listed exemptions and adds extra rules for location services; if a path does not work on these phones, the architecture changes before accounts and groups are built.

**Independent Test**: stop the service, keep the app in the background with the screen off for 10 minutes, send a high-priority push from the test page; the phone starts sharing and a new point arrives, or the phone shows "Tap to share" and the journal records why.

**Acceptance Scenarios**:

1. **Given** the service is not running and the app is in the background, **When** the observer sends a high-priority wake message, **Then** the phone checks the delivered priority, starts sharing with a visible notification if allowed, and a new point arrives within 60 seconds in at least 70% of attempts (NFR-LOC-05); otherwise it shows a notification asking the owner to tap to share.
2. **Given** each background start path of scenario D-27, **When** it is triggered with and without the battery-optimization exemption, **Then** the run report records for each phone whether the service started, whether it received locations, and the exception if it was refused.
3. **Given** wake messages are sent during the spike, **When** they arrive, **Then** the engine journal records the delivered priority, so downgraded high-priority messages are counted.

---

### User Story 5 - Produce run reports and a decision (Priority: P2)

For each device scenario the owner exports the engine journal (no coordinates), combines it with the server statistics and the battery report, and stores a run report in the feature folder. At the end, measured values replace the starting targets in the NFR document, engine parameters are adjusted, and a go / adjust / rethink decision is written down.

**Why this priority**: the spike's value is the evidence and the decision, not the code.

**Independent Test**: after a walk, export the journal and generate the run report; it contains mode transitions, upload results and measured freshness for that run.

**Acceptance Scenarios**:

1. **Given** a finished scenario, **When** the owner exports the journal, **Then** the export contains mode changes, starts and stops with reasons, upload results and buffer sizes, and no coordinates.
2. **Given** all stage-2 scenarios ran on both phones with default settings and after the instructions, **When** the results are summarized, **Then** each NFR target listed in this spec has a measured value or an explanation why it could not be measured.
3. **Given** the summary, **When** the owner decides, **Then** the decision (go, adjust parameters, or rethink the approach) and its reasons are recorded in this feature folder.

---

### Edge Cases

- Permission downgraded to "only while using the app" or to approximate location while sharing: the phone reports the status and stops or degrades as described in the [permissions rationale](../../docs/privacy/permissions-rationale.md); the observer sees the reason.
- Location turned off in system settings: the status is reported within one heartbeat; sharing resumes when location is turned on.
- GPS fixes with poor accuracy indoors: points are flagged, not dropped; they do not trigger a switch to a moving mode.
- Mock locations: points are flagged and sent, not rejected.
- Battery below 15% and not charging: intervals double and the active-view lease is limited to 2 minutes.
- Tunnel or PC offline during an outdoor test: the phone buffers and resends later; the run report notes the outage.
- A device secret leaks: the owner replaces it; old secret is rejected afterwards.
- Two reports from the same device arrive concurrently: the stored state is the same as if they arrived in order.

## Requirements *(mandatory)*

### Functional Requirements

**Phone**

- **FR-001**: The app MUST let the owner turn sharing on and off; sharing MUST start only after the owner turns it on and grants the location permissions.
- **FR-002**: While sharing is on, the app MUST show a persistent notification that states location is being shared and offers a Pause action; the notification MUST NOT be hidden or disguised.
- **FR-003**: Pause MUST stop collection immediately and report the paused status once; resume MUST restart collection.
- **FR-004**: The phone MUST select one of the modes stationary, moving on foot, moving in a vehicle and active view, with the transitions, intervals and priorities of [Android architecture §5.1](../../docs/architecture/android.md) for the Balanced profile.
- **FR-005**: Mode parameters MUST come from the remote configuration with built-in defaults, so they can be changed without a new build.
- **FR-006**: In stationary mode the phone MUST NOT keep an active high-accuracy location request; it MUST send a heartbeat with its status at least every 15 minutes.
- **FR-007**: The phone MUST drop points without accuracy, stale cached fixes, duplicates and out-of-order points, and MUST flag (not drop) low-accuracy, outlier, mock and approximate points.
- **FR-008**: The phone MUST keep points not yet confirmed by the server for up to 7 days or 10 000 points, dropping the oldest first when the limit is reached.
- **FR-009**: The phone MUST send the newest point first, then older points in batches of up to 200, retry with increasing delays, and delete points only after the server confirms them.
- **FR-010**: Each point MUST carry its age at the moment of sending; points that survived a reboot MUST be marked as using wall-clock time.
- **FR-011**: Each report MUST include the device status (sharing state, mode, battery level and charging, location enabled, permission level, power saving) and the promised time until the next report.
- **FR-012**: Sharing MUST restart after reboot and after an app update when it was on before, and a periodic watchdog MUST restart it or notify the owner when it is not running but should be.
- **FR-013**: When the phone's location was requested, the phone MUST show a notification about the request.
- **FR-014**: Below 15% battery and not charging, moving intervals MUST double and the active-view lease MUST be limited to 2 minutes.
- **FR-015**: The development build MUST have an engine journal screen (mode changes, starts and stops with reasons, upload results, buffer size, drops) without coordinates, and MUST export the journal as a file.
- **FR-016**: The app MUST contain written instructions for the phone-maker settings of P1 and P2 that keep sharing alive, based on what is found during this feature.
- **FR-028**: The phone MUST receive wake messages through the push channel; before starting the service from a wake message it MUST check that the message was delivered with high priority, and otherwise MUST show a notification asking the owner to tap to share.
- **FR-029**: The engine journal MUST record for every start of the service its trigger (user action, boot, app update, push, geofence, watchdog, alarm, notification action) and, when a start is refused, the exception; for every wake message the requested and the delivered priority.

**Server**

- **FR-030**: The owner MUST be able to send a high-priority or a normal-priority wake message to a test device from the test page.

**Server**

- **FR-017**: The server MUST accept device reports and push tokens only from registered test devices, each authenticated by its own secret configured by the owner.
- **FR-018**: The server MUST store each point once even when a batch is sent repeatedly, and MUST compute each point's time from its age and the server's receive time.
- **FR-019**: The server MUST keep a current position per device that only moves to a newer point.
- **FR-020**: The server MUST store device status and the promised next report time, and compute freshness from them (now, recent, stale, no contact) as described in [ADR-008](../../docs/architecture/adr/ADR-008-server-computed-time-and-freshness.md).
- **FR-021**: The server MUST return pending commands (active-view lease, fresh-location request) in the response to a device report.
- **FR-022**: The server MUST remove points older than the retention period (30 days by default) at least daily.
- **FR-023**: The owner MUST be able to request a fresh location and see per device: latest position, track for a chosen period, freshness, mode timeline, heartbeat intervals, gaps and duplicate count — only after presenting the owner's secret.
- **FR-024**: Logs and error reports MUST NOT contain coordinates, device secrets or tokens; this MUST be covered by tests.
- **FR-025**: The local server MUST be reachable from phones outdoors through a stable HTTPS address that the owner can turn on and off.

**Evidence**

- **FR-026**: Run reports for the stage-2 scenarios of the device test matrix MUST be stored in this feature folder for both phones, with default settings and after the instructions.
- **FR-027**: Measured values MUST be written back into the NFR document and the default engine parameters, and the go / adjust / rethink decision MUST be recorded.

### Key Entities

- **Test device**: a registered phone with its secret, model and distribution variant.
- **Location point**: position, accuracy, speed, flags, age at sending, time source; stored once per device and point identity.
- **Current position**: the newest point of a device.
- **Device status**: sharing state, mode, battery, location setting, permission level, power saving, promised next report time, last contact.
- **Command**: an instruction for a device (active-view lease with expiry, fresh-location request) delivered with the next report response.
- **Engine journal entry** (on the phone): time, event type, mode, reason, upload result, buffer size; never coordinates.
- **Run report**: scenario ids, device, build, settings variant, measured values, result, evidence links.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: While walking or riding with the phone locked, the median age of the newest point is ≤ 90 s and the 95th percentile ≤ 5 min on both phones after the instructions are applied.
- **SC-002**: During an 8-hour still night, at least 95% of heartbeats arrive within 20 minutes of the previous one, and the battery drop is ≤ 1%.
- **SC-003**: Battery use is measured per mode (percent per hour in stationary, moving and active view); over a typical day the app uses ≤ ~5% of battery on each phone, or the deviation and its cause are documented.
- **SC-004**: After 2 hours without network, 100% of points recorded during the gap reach the server and 0 duplicates are stored.
- **SC-005**: Sharing restarts after reboot and after swiping the app away on both phones (after instructions) in every attempt.
- **SC-006**: At least 90% of fresh-location requests to a phone with a running service produce a new point within 30 seconds.
- **SC-007**: Movement start is detected with a median ≤ 3 min over 10 departures per phone.
- **SC-008**: Every stage-2 scenario of the device test matrix has a run report for both phones, and the owner has recorded a go / adjust / rethink decision.
- **SC-009**: At least 70% of high-priority wake messages to a phone whose service is not running produce a new point within 60 seconds (NFR-LOC-05), or the cause is documented.
- **SC-010**: The table of background start paths (D-27) is complete for both phones, with and without the battery-optimization exemption.

## Assumptions

- No accounts and no groups: the owner registers two or three test devices with secrets; the location data is the owner's own.
- Only the Balanced frequency profile is in scope; Economy and Frequent come later.
- The push channel is minimal: a Firebase project created by the owner, a push token per test device, wake messages sent from the test page. Push for alerts and group events comes with the features that need them.
- The effect of the unused-app restrictions ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)) takes months and is not measured here; it is handled by onboarding and the self-check in stage 5.
- The onboarding disclosure is minimal in this feature (the owner is the only user), but the permission flow already follows the store rules; the full onboarding comes in stage 5.
- The server runs on the developer PC; outdoor access uses the tunnel chosen in [ADR-019](../../docs/architecture/adr/ADR-019-hosting-oracle-always-free.md).
- Starting values of all parameters come from the architecture documents and may change with the measurements; changing them is part of this feature, not a change of requirements.

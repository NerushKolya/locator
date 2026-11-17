# Device test matrix

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [NFR](nfr.md) · [testing strategy](testing-strategy.md) · [Android architecture §5](../architecture/android.md) · [user journeys](../product/user-journeys.md)

Background location is verified **only on real phones**. Emulators do not reproduce phone-maker firmware, real Doze, real GPS and radio, or battery drain. A feature that touches background location is not done until its scenarios below pass ([development workflow](../ops/development-workflow.md)). A successful APK build proves nothing about tracking.

## 1. Devices

| ID | Device | System | Role |
|---|---|---|---|
| P1 | OnePlus 13 | OxygenOS (Android 15) | Sharing phone A |
| P2 | RedMagic 8S Pro | RedMagic OS (Android 15) | Sharing phone B |
| B-* | Phones of the beta participants | As listed by the owner before stage 5 (maker, model, Android version) | Phone-maker instructions in onboarding and D-18 cover each maker on this list; D-16 runs once on each phone in the beta |
| E29, E30 | Emulator, API 29 and API 30 | Google APIs image | Old-Android code paths (background permission flow, foreground service rules) — functional checks only |
| E-latest | Emulator, latest API | Google Play image | Functional checks, Maestro flows with `setLocation` (API 31+) |

Exact OS versions are recorded in each run report (`adb shell getprop ro.build.version.release` and the firmware version from the settings).

## 2. How to run a scenario

- **Build:** `internal` flavor, release build type (minified, as the beta gets it) unless stated otherwise.
- **Two runs per phone:** (a) default phone settings; (b) after following the in-app battery and phone-maker instructions.
- **Observer:** a second phone or the back office with the map of the group open, unless the scenario says "map closed".
- **Evidence:** engine journal export (no coordinates), server log of device reports for the device id, `adb shell dumpsys batterystats` where battery is involved, screenshots of what the observer saw.
- **Result:** pass / fail / partial with the measured value; failures become issues linked to the spec.

Columns below: **Steps** · **Expected on the phone** · **What other members see** · **How to verify**.

## 3. App and process state

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-01 | App on screen | Sharing on, map open, walk 15 min | Persistent notification shows "Sharing"; mode `moving_foot`; point about every 60 s | Marker moves; status "Now" | Server: points ≤ 60–90 s apart; observer screenshots |
| D-02 | App minimized | Press Home, keep screen on, walk 15 min | Same as D-01 | Same as D-01 | Same as D-01 |
| D-03 | Screen locked, phone in pocket | Lock, pocket, walk 30 min | Collection continues; notification present | Fresh marker; position age within NFR-LOC-01 | Gaps in server points; NFR-LOC-01 statistics |
| D-04 | Night on the table | Not charging, 8 h, no interaction (Doze) | `stationary`; heartbeat every 15 min | "At place since HH:MM, checked N min ago"; no "No contact" | Heartbeat intervals (NFR-LOC-02); battery drop (NFR-BAT-02) |
| D-05 | Swiped from recents | Swipe the app away from recent apps, then walk 15 min | Service keeps running (foreground service is not tied to the task) | No change | Points continue; journal has no stop event. Record phone-maker behaviour for default vs. instructions |
| D-06 | Reboot | Reboot, do **not** open the app, walk 15 min | Service starts after boot; notification back | Brief "Stale" at most, then fresh | Time from boot to first point; journal start reason `boot` |
| D-07 | Force stop | Settings → App → Force stop; wait 1 h; then open the app | Nothing runs (Android rule); on next open the app offers to resume | "Stale", then "No contact since HH:MM"; one "no updates" alert to subscribers | Status timeline on the server; alert count = 1 |
| D-24 | App update while sharing | Install a newer build over the running one | Service restarts after the update | No more than one missed heartbeat | Journal start reason `package_replaced` |

## 4. Location, network and connectivity

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-08 | Location turned off | Turn off device location for 15 min, then on | Notification "Location is off"; service reports status; resumes when on | Reason icon "Location off"; last position with its age | Status event within one heartbeat (NFR-LOC-09) |
| D-09 | No internet while moving | Mobile data and Wi-Fi off, walk/drive 2 h, then reconnect | Points buffered; on reconnect newest first, then older in batches | During the gap: "Stale" → "No contact"; after: fresh marker at once, no jump backwards | Server: no gaps, no duplicates (NFR-LOC-07, NFR-LOC-08) |
| D-10 | Wi-Fi ↔ mobile switching | Walk out of Wi-Fi range and back three times | No lost points; retries succeed | No visible effect | Server gap analysis |
| D-19 | Permission changed | (a) Revoke location permission; (b) set "Only while using the app"; (c) switch to approximate location | Sharing stops with a clear notification; app explains how to fix | Reason icon "No permission" / "Approximate" | Status events; app screens match [permissions rationale](../privacy/permissions-rationale.md) |
| D-20 | Poor GPS | 30 min inside a large building; a metro ride if available | Points flagged low accuracy; no false switch to moving | Accuracy circle grows; no jumping marker beyond accuracy | Count of false `moving_*` transitions = 0; accuracy values |
| D-22 | Clock changed | Set phone time ±1 h manually, send points, restore | Points still carry correct age | Correct "N min ago" | Server `fixed_at` vs. real time within accuracy of the age model |

## 5. Power and phone-maker restrictions

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-11 | Battery saver | Turn on battery saver; screen off; walk 30 min | Collection continues, possibly less often; status "Power saving" reported | Extra icon "Updates less often" | Points and status; compare frequency with D-03 |
| D-17 | Low battery | Discharge below 15%, not charging, walk 15 min | Low-battery modifier: intervals × 2, heartbeat 30 min | Battery level shown; one low-battery alert to subscribers | Journal mode modifiers; alert count = 1 |
| D-18 | Phone-maker restrictions | P1: auto-launch, battery optimization, app lock in recents on OxygenOS; P2: equivalents on RedMagic OS. Record default values, then apply instructions and repeat D-05 and D-06 | After instructions D-05, D-06 pass | — | Screenshots and exact settings paths for the in-app instructions |

## 6. Movement and long stays

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-12 | Leaving home | Phone stationary ≥ 30 min, then leave and walk; repeat 10 times on different days | Switch to `moving_foot` after a confirming control fix | Marker starts moving | Delay statistics (NFR-LOC-03) |
| D-13 | Vehicle trip | Ride ≥ 30 min by car or public transport | `moving_vehicle` after 3 fast points; back to `moving_foot`/`stationary` after the trip | Marker follows the route; status "Now"/"Recent" | Freshness statistics (NFR-LOC-01); mode timeline |
| D-21 | Long stay at a place | 4–8 h at work or at home with the phone mostly still | `stationary`; no false movement | "At place since HH:MM, checked N min ago" | 0 false `moving_*` transitions; heartbeats (NFR-LOC-02) |
| D-16 | Typical day | 24 h of normal life without touching the app | Survives the whole day | Plausible track; no unexplained "No contact" | Battery (NFR-BAT-01); time per mode; survival (NFR-LOC-06) |

## 7. Requests, pause and SOS

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-14 | Fresh location, service running | Phone A screen off for 5 min / 1 h / overnight; phone B requests a fresh location | Notification "N requested your location"; `active_view` for 2 min | New point within 30 s | Success ratio and latency (NFR-LOC-04); access-log entry |
| D-15 | Fresh location, service not running | Stop the service (pause ended without restart, or killed by firmware); request from phone B | Push wakes the app; service starts if Android allows, else a notification "Tap to share" | New point within 60 s, or a clear "Could not reach the phone" | Success ratio and latency (NFR-LOC-05) |
| D-23 | Pause and resume | Pause for 15 min from the notification; wait; let the timer end. Run with and without the battery-optimization exemption | Collection stops at once; the service stops and a quiet "Paused until HH:MM · Resume" notification stays; at the end sharing resumes through the end-of-pause push or the alarm, otherwise "Pause ended — tap to resume" | "Paused since HH:MM", position hidden per reciprocity rules | Status events; no points stored during the pause; journal shows which path resumed sharing |
| D-25 | SOS while paused | Pause, then start SOS; walk 5 min; end SOS | SOS overrides the pause; points every 5 s | SOS alert to all members of all groups; live marker | Alert delivery time; points during SOS only |

## 8. Platform rules that limit background work

These scenarios check Android rules rather than phone-maker behaviour. Background start of a foreground service is allowed only in the exemptions listed in [restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start); a service of type `location` started from the background has extra "while-in-use" rules on Android 14+, and the official wording leaves room for interpretation, so only the phones decide.

| ID | Scenario | Steps | Expected on the phone | What other members see | How to verify |
|---|---|---|---|---|---|
| D-26 | Unused-app restrictions | Check the self-check item for "Pause app activity if unused" with default settings; open the fix screen; turn the option off; return | Self-check shows the restriction as active by default and as off after the fix; status reported to the server | Nothing (the operator sees the self-check summary) | Self-check status matches the system setting on P1 and P2. The months-long effect itself cannot be reproduced in a test ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)) |
| D-27 | Background start paths | With the service stopped and the app in the background, trigger each path: boot completed, app update, high-priority push, normal-priority push, exit of the anchor geofence, WorkManager watchdog, inexact alarm, tap on a notification action. Run each **with and without** the battery-optimization exemption | For each path: service started and location received / start refused (exception recorded) / service started without location access | — | Table "path × exemption × phone → result" in the run report; journal records the start reason, the exception class and the delivered push priority |
| D-28 | Notification permission denied | Android 13+: deny notification permission in onboarding; share for 1 h; send requests and alerts | Sharing works; the persistent notification is not shown in the shade; alerts are not shown; self-check marks it red | Normal statuses | Self-check state; journal shows delivered push priorities (high-priority messages are expected to be downgraded, [FCM message priority](https://firebase.google.com/docs/cloud-messaging/android/message-priority)) |

## 9. Coverage of the roadmap

| Roadmap stage | Scenarios that must pass |
|---|---|
| 2 — location spike | D-01…D-07, D-09…D-18, D-20…D-22, D-24, D-27 (requests via a test endpoint; push through the minimal FCM channel) |
| 4b — realtime map | D-14, D-15 with the real request flow |
| 5 — pause, privacy, onboarding | D-08, D-19, D-23, D-26, D-28 + re-run D-05, D-06, D-18 with the onboarding instructions |
| 6 — places, SOS, alerts | D-17, D-25; D-15 with revival pushes from the watchdog |
| 9 — family beta | Full matrix on both phones; D-16 on every beta participant's phone once |
| 11 — store release | Full matrix with the `store` flavor ("Only on request" variants of D-14, D-15) |

## 10. Run report template

```markdown
### Run <date> · <scenario ids> · <device> · build <version, flavor>
Settings: default | after instructions
| Scenario | Result | Measured | Notes |
|---|---|---|---|
| D-03 | pass | median age 72 s, p95 3.9 min | — |
Evidence: journal export, server query, screenshots (links)
```

Run reports are stored in the feature spec folder of the stage (`specs/NNN-…/device-runs.md`).

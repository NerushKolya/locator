# Non-functional requirements

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [vision §9](../product/vision.md) · [Android architecture §5](../architecture/android.md) · [security §11](../architecture/security.md) · [testing strategy](testing-strategy.md) · [device test matrix](device-test-matrix.md)

Every requirement has an ID, a measurable target, a way to measure it and the moment it is first verified. Values marked *(spike)* are starting targets: the location spike (roadmap stage 2) measures them on the real test phones and this document is updated with the measured values. Values marked *(config)* are tunable without an app release.

**Typical day** (reference for battery and traffic): 21 h stationary (night, work, home), 1.5 h on foot, 1 h in a vehicle, 0.5 h of other members viewing the map in total, 2 fresh-location requests.

## 1. Location delivery

| ID | Requirement | Target | How measured | First verified |
|---|---|---|---|---|
| NFR-LOC-01 | Freshness while moving: age of the newest stored point of a moving person | Median ≤ 90 s; 95th percentile ≤ 5 min *(spike)* | Server-side: `now − fixed_at` sampled every minute during walks and drives (scenarios D-03, D-13) | Spike |
| NFR-LOC-02 | Stationary heartbeat | At least every 15 min; ≥ 95% of heartbeats arrive within 20 min *(spike)* | Server log of device reports during an 8-hour night (D-04) | Spike |
| NFR-LOC-03 | Detection of the start of movement | Median ≤ 3 min; 95th percentile ≤ 6 min after leaving the anchor *(spike)* | 10 departures from home per phone (D-12); engine journal vs. manual timestamp | Spike |
| NFR-LOC-04 | Fresh location on request, service running | ≥ 90% of requests produce a new point within 30 s | Requests from a second phone at screen-off 5 min / 1 h / night (D-14) | Spike |
| NFR-LOC-05 | Fresh location on request, service not running (push wake-up) | ≥ 70% within 60 s *(honestly expected to be worse than NFR-LOC-04)* | Same, after the service was stopped (D-15) | Spike |
| NFR-LOC-06 | Survival | The service survives a night, a reboot and 24 h in a pocket without user action, after the phone is set up by our instructions | D-05, D-06, D-16 on both phones, default settings and after instructions | Spike, beta |
| NFR-LOC-07 | No loss, no duplicates | 0 points lost after up to 24 h without network; 0 duplicates stored | D-09, D-10; server query for gaps and duplicate point ids | Spike |
| NFR-LOC-08 | Order | A late point never moves the current marker backwards | Integration test + D-09 | Spike (automated) |
| NFR-LOC-09 | Honest status | When the phone reports a reason (paused, location off, permission revoked, power saving, unused-app restrictions active), group members see it within one heartbeat | D-08, D-11, D-19, D-23, D-26 | Stage 5 |
| NFR-LOC-10 | High priority kept | ≥ 95% of high-priority push messages are delivered with high priority over any 7 days, measured per phone *(spike)*. High priority is used only for messages that end in something the person sees: an explicit fresh-location request, SOS, place alerts, revival and the end of a pause ([ADR-014](../architecture/adr/ADR-014-push-as-wake-channel.md)) | Engine journal: requested vs. delivered priority (D-15, D-27); beta statistics | Spike, beta |

## 2. Battery and data usage

| ID | Requirement | Target | How measured | First verified |
|---|---|---|---|---|
| NFR-BAT-01 | Battery use on a typical day | ≤ ~5% of battery per 24 h attributable to the app *(spike)*. This is the owner's target; the typical day above contains about 3 h of high-accuracy location, so the daily figure is computed from the per-mode values of NFR-BAT-04 and may need different intervals | Day without the app vs. day with the app on the same phone; `adb shell dumpsys batterystats` after `--reset`; engine journal (time per mode) | Spike, beta |
| NFR-BAT-02 | Stationary night | ≤ 1% per 8 h stationary *(spike)* | D-04 | Spike |
| NFR-BAT-03 | Low-battery modifier | Below 15% and not charging, moving intervals double and active view is limited to 2 min *(config)* | Unit tests of the mode controller; D-17 | Spike |
| NFR-BAT-04 | Battery use per mode | Measured and recorded: percent per hour in `stationary`, `moving_foot`, `moving_vehicle` and `active_view` on each test phone. No target is set before the spike; the values drive the interval choices and the daily estimate of NFR-BAT-01 | Separate runs per mode with the screen off; `dumpsys batterystats`; engine journal | Spike |
| NFR-DATA-01 | Mobile data per person | ≤ 3 MB per typical day; ≤ 90 MB per month *(estimate)* | Android data usage screen for the app over one week | Beta |

## 3. Server and realtime

| ID | Requirement | Target | How measured | First verified |
|---|---|---|---|---|
| NFR-SRV-01 | Device report latency (server) | p95 ≤ 300 ms at beta load | Request duration in structured logs | Stage 4a |
| NFR-SRV-02 | Other API endpoints | p95 ≤ 500 ms at beta load | Same | Stage 4a |
| NFR-SRV-03 | Realtime delivery: point accepted → event on an open map stream | p95 ≤ 2 s | End-to-end backend test with timestamps; manual check with two phones | Stage 4b |
| NFR-SRV-04 | Revocation reaches streams | A removed member's or a paused viewer's stream closes within 5 s | Automated test (security criterion 2) | Stage 4b |
| NFR-SRV-05 | Capacity | 100 people sharing at once on one VM with ≤ 2 GB memory used by the stack. Peak load ≈ 10 device reports per second: ≈ 2 per second from normal intervals plus active view (each viewed member reports every 10 s; e.g. 10 groups viewed at once × 8 members) | Load script against the local stack before the beta | Stage 8 |
| NFR-SRV-06 | Memory budget | All five containers together ≤ 1.5 GB resident at beta load | `docker stats` | Stage 1 (baseline), stage 8 |

## 4. Availability and recovery

| ID | Requirement | Target | How measured | First verified |
|---|---|---|---|---|
| NFR-AV-01 | Availability of the public API (beta and store release) | ≥ 99% per calendar month, excluding announced maintenance. One free VM gives no stronger guarantee | Uptime monitor on `/health` | Beta |
| NFR-AV-02 | Recovery point objective | ≤ 24 h of lost data | Nightly backups ([ADR-022](../architecture/adr/ADR-022-backups-encrypted-two-locations.md)) | Stage 8 |
| NFR-AV-03 | Recovery time objective | ≤ 4 h from "VM lost" to service restored on a new host | Restore drill once before the beta, then yearly | Stage 8 |
| NFR-AV-04 | Restore test | A monthly restore of the latest dump into a clean environment passes the backend test suite | Restore log | Stage 8 |
| NFR-AV-05 | Degraded mode | When the stream is unavailable, the map falls back to polling every 30 s; when tiles fail, the member list with statuses still works | Automated UI test; manual check with tiles blocked | Stage 4b |

## 5. Data volume and retention

| ID | Requirement | Target | How measured | First verified |
|---|---|---|---|---|
| NFR-DV-01 | Stored points | ≈ 300 points per person per day *(estimate)*; ≈ 300 MB with indexes for 100 people × 30 days ([data model §5](../architecture/data-model.md)) | Table sizes in the database | Beta |
| NFR-DV-02 | Retention | Points older than the retention period (30 days by default) are removed daily | Integration test of the retention job | Stage 4a |
| NFR-DV-03 | Account deletion | All location data of a deleted account is gone within 10 min | Test that searches every table (security criterion 8) | Stage 5 |
| NFR-DV-04 | Phone buffer | Up to 7 days or 10 000 points kept while offline *(config)* | Unit test of the buffer | Spike |

## 6. Security and privacy

The security acceptance criteria in [security.md §11](../architecture/security.md) are part of these requirements. In addition:

| ID | Requirement | Target | How measured |
|---|---|---|---|
| NFR-SEC-01 | Group boundary | 0 endpoints or stream classes that reveal a position outside a shared group | Authorization matrix suite in CI, blocking |
| NFR-SEC-02 | Scrubbing | 0 coordinates, place names, tokens, e-mail addresses or invitation codes in logs and error reports | Scrubbing tests per service and in the app |
| NFR-SEC-03 | Dependencies | No known critical vulnerabilities at release | Dependency audit in CI |
| NFR-SEC-04 | Store build | The `store` artifact contains no operator classes | Build check in CI |

## 7. App quality

| ID | Requirement | Target | How measured |
|---|---|---|---|
| NFR-APP-01 | Compatibility | Android 10 (API 29) to the current target (API 36); Google Play services required; unsupported devices get a clear message | Emulator tests on API 29 and the latest API; both test phones |
| NFR-APP-02 | Crash-free sessions | ≥ 99% crash-free users per week during the beta | Error tracker |
| NFR-APP-03 | Cold start to map with cached data | ≤ 2 s on the test phones | Manual measurement with a stopwatch, then a macrobenchmark if needed |
| NFR-APP-04 | Localization | 100% of user-visible strings in Ukrainian and English; no hard-coded strings | Lint rule + screenshot tests in both languages |
| NFR-APP-05 | Accessibility | Touch targets ≥ 48 dp; content descriptions on icons; layouts work at 200% font size; status never conveyed by color alone | Compose accessibility checks + screenshot tests at large font |
| NFR-APP-06 | APK size | Release APK ≤ 30 MB *(estimate; the map library dominates)* | CI artifact size |

## 8. What is not promised

- Continuous tracking under all conditions: Android and phone makers may stop background work; the app detects and reports it.
- Any update after the user force-stops the app.
- Accuracy indoors or in the metro.
- Availability beyond what one free VM gives (NFR-AV-01). SOS travels through the server: while the server is unreachable an SOS is queued on the phone ("Not delivered yet") and the SOS screen keeps the one-tap emergency call.
- Sharing on a phone whose owner does not open the app for months and has not turned off the system's unused-app restrictions: Android may reset the permissions and block push messages ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)). Onboarding and the self-check offer to turn the restriction off; the group sees the silence as "No contact".

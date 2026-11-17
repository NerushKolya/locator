# Risk register

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [roadmap](roadmap.md) · [feature matrix](../product/feature-matrix.md) · [security](../architecture/security.md) · [NFR](../quality/nfr.md)

Probability and impact: **L** low, **M** medium, **H** high, **C** critical (impact only). Reviewed at the end of every roadmap stage.

## 1. Top five

1. **R-01** — phone firmware stops background sharing.
2. **R-11** — an authorization bug shows a location outside a shared group.
3. **R-02** — battery use above the target makes the family turn sharing off.
4. **R-06** — Google Play rejects the background-location declaration.
5. **R-08** — the free server is reclaimed or the free tier changes.

## 2. Register

| ID | Risk | P | I | Mitigation | Early signal | Stage | Status |
|---|---|---|---|---|---|---|---|
| R-01 | Phone-maker firmware (OxygenOS, RedMagic OS and the makers of the beta participants' phones) kills or delays the location service | H | H | Foreground service of type `location`; self-recovery layers; phone-maker instructions in onboarding for every maker on the participants' list; self-check; honest "No contact" status; spike on both phones before anything else | D-05, D-06, D-16 fail with default settings | 2, 5, 9 | Open |
| R-02 | Battery use above ~5% per day | M | H | Stationary mode without active requests; adaptive intervals; active-view lease limits; parameters in remote configuration; measurement per mode | NFR-BAT-01/02 exceeded in the spike | 2, 9 | Open |
| R-03 | Push wake-up delayed or dropped (Doze, firmware, FCM changes) | M | M | Push only as a wake signal; high-priority data messages used sparingly; honest "Could not reach the phone"; fresh location works without push while the service runs | NFR-LOC-05 below 70%; D-27 shows that push does not start the service | 2, 4b | Open |
| R-04 | Late detection of movement or false exits from stationary mode | M | M | Anchor geofence + control fix confirmation; optional activity recognition if the spike requires it | NFR-LOC-03 missed; false transitions in D-20, D-21 | 2 | Open |
| R-05 | Streams broken by proxies or mobile networks (buffering, idle timeouts) | M | M | SSE heartbeats; polling fallback every 30 s (the first vertical slice works on polling alone); tested through the edge proxy and over mobile data | Stream reconnects in logs; NFR-SRV-03 missed | 4b | Open |
| R-06 | Google Play rejects the background-location or foreground-service declaration, or policy changes | M | H | Compliance from day one; operator code absent from the store build; "Only on request" mode; review video; follow the policy pages before submission | Reviewer questions; policy update e-mails | 11, 12 | Open |
| R-07 | False or noisy alerts (places at night, "no updates" while the phone sleeps) | M | M | Server-side hysteresis; thresholds from promised heartbeat; one alert per episode; thresholds tuned in the beta | Beta complaints; alert counts per day | 6, 9 | Open |
| R-08 | Free VM reclaimed or free tier reduced | M | H | VM shape chosen so the stack's memory stays above the idle threshold ([deployment §4](../ops/deployment.md)); infrastructure as code; nightly off-site backups; plan B home PC, plan C paid VPS | Idle warnings; utilization graphs near the threshold; provider announcements | 8 | Open |
| R-09 | Map tiles unavailable or poor for Ukraine | L | M | Style URL in remote configuration; member list works without tiles; plan B self-hosted tiles | Prototype review in stage 1; tile errors | 1 | Open |
| R-10 | Free domain problems (app-link verification, certificate limits, availability) | M | M | deSEC subdomain now, `eu.org` application in advance; app links verified in stage 3; invitation codes work without links | Verification failures | 3 | Open |
| R-11 | Authorization bug reveals a location outside a shared group or to a non-sharing member | L | C | Single visibility policy; authorization matrix for every endpoint and stream, blocking CI; independent review before the beta | Any matrix gap found in review | 3–7 | Open |
| R-12 | Slow backend development on Windows bind mounts | M | L | Dependencies in named volumes; plan B: backend sources in a WSL file system | Test runs much slower than in CI | 1 | Open |
| R-13 | Oracle registration requires a payment card the owner cannot or does not want to use | M | M | Decide before stage 8; plan B home PC + tunnel | Registration blocked | 8 | Open |
| R-14 | No independent reviewer for security-sensitive code | M | M | Ask a developer friend early; structured review checklist; time-boxed external review | No reviewer by stage 7 | 7 | Open |
| R-15 | Google sign-in and OAuth configuration errors | L | M | Follow official Credential Manager guides; test on both phones and the emulator in stage 3 | Sign-in fails on one device | 3 | Open |
| R-16 | Participants feel watched and stop using the app | L | H | Reciprocity, visible requests, transparency screen, operator disclosure before installing | Beta interview answers | 9 | Open |
| R-17 | History (the most sensitive data) leaks or is retained too long | L | H | History private by default; separate visibility level; retention job tested; encryption of older history reconsidered in v1.1 | Retention job failures | 10 | Open |
| R-18 | Not enough testers for the closed test (12 for 14 consecutive days) | M | M | Recruit family and friends early; keep a reserve list; remind testers to stay opted in | Fewer than 14 willing testers before stage 11 | 11 | Open |
| R-19 | Platform changes: Android background limits, FCM API changes, yearly `targetSdk` requirement | M | M | Follow Android and Firebase release notes; wake channel and location source behind interfaces; yearly update task | Deprecation notices | 12 | Open |
| R-20 | Secret leaked in the public repository | L | H | `.env` and keystores ignored; secret scanning locally and in CI; rotation procedure | Scanner finding | All | Open |
| R-21 | Solo project stalls (scope creep, fatigue) | M | M | Small stages with visible demos; family alpha from stage 4b; back office off the critical path; no deadlines; MVP scope fixed in the feature matrix; new ideas go to "Later" | A stage without progress for a long time | All | Open |
| R-22 | Development tooling for specifications changes incompatibly | M | L | Tool version pinned; specs are plain Markdown and remain usable without the tool; review after the first features | Breaking release of the tool | 1–4 | Open |
| R-23 | Android puts the app into hibernation on a phone whose owner does not open it for months: permissions reset, push and jobs blocked ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)); whether a running foreground service counts as use is not stated by the platform | M | H | Onboarding step and self-check item to turn off the unused-app restrictions; status in the device report, visible to the operator; honest "No contact" status; beta participants reminded | Self-check reports the restriction active on participants' phones; a participant goes silent after weeks without opening the app | 5, 9 | Open |
| R-24 | FCM downgrades the app's high-priority messages, so fresh-location requests, revival and the end of a pause stop starting the service | M | M | High priority only for messages that end in a visible notification ([ADR-014](../architecture/adr/ADR-014-push-as-wake-channel.md)); requested vs. delivered priority recorded on the phone (NFR-LOC-10); notification permission checked by the self-check | Share of downgraded messages grows in the journal or the operator statistics | 2, 9 | Open |

## 3. Closed risks

None yet.

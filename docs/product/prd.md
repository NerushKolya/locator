# Product requirements (PRD)

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [vision](vision.md) · [personas](personas.md) · [user journeys](user-journeys.md) · [feature matrix](feature-matrix.md) · [glossary](glossary.md)

## 1. How to read this document

- **Business rules (BR-xx)** in §2 apply to every story. A story never overrides a business rule; a conflict is a bug in the story.
- **User stories (US-xxx)** in §4–§16 are grouped by epic. Each has a release, a priority and acceptance criteria in Given/When/Then form.
- **Release:** `MVP` (family beta) · `v1.1` (history) · `Play` (Google Play release) · `v2` · `Later` — see [feature matrix](feature-matrix.md).
- **Priority within a release:** `Must` — the release does not ship without it · `Should` — expected, may slip to the next release · `Could` — only if cheap.
- Numbers marked *(config)* are server-side configuration defaults, not constants in the app. Numbers marked *(spike)* are starting values that the location spike on real phones confirms or corrects.
- Feature specifications (`specs/NNN-<feature>/`) refine these stories into detailed requirements; when a spec changes a story, this PRD is updated in the same change.

## 2. Business rules

| ID | Rule |
|---|---|
| **BR-01** | **Group boundary.** A user can receive another user's location data (position, place alerts, SOS position, history) only through a group in which both are current members. Enforced on the server for every endpoint and stream; covered by automated authorization tests. Exception: BR-11. |
| **BR-02** | **Reciprocity.** In group G, viewer V sees member M's position only while V's own sharing is active (`Always`, or `On request` in the Play release) and M is sharing. While V is paused or not sharing, V sees members' names and statuses but no positions or place alerts. **Exceptions:** SOS (BR-07) and the operator (BR-11). |
| **BR-03** | **Consent.** Location is collected only after the person turns sharing on themselves. Accepting a group invitation is an explicit consent to share with that group; the invitation screen says so. In the MVP one sharing switch applies to all of the person's groups; a per-group switch comes in v1.1. |
| **BR-04** | **Visible requests.** Every fresh-location request and every explicit operator action concerning a person (fresh-location request, remote turn-on, history view) (a) shows a notification on that person's phone and (b) is recorded in their access log. The operator's passive live overview (the back-office map of all users) is recorded in the access log only — one entry per person per day while the operator watches live data — without a phone notification; beta participants are told about this before installing (US-090). Access-log entries are kept for 90 days *(config)*. |
| **BR-05** | **Persistent notification.** While sharing is active, the Android persistent notification is shown. It is never hidden or disguised. It offers a one-tap pause. |
| **BR-06** | **Server time is the truth.** A position's time is computed by the server from the point age reported by the phone (monotonic clock), not from the phone's wall clock. |
| **BR-07** | **SOS reaches everyone.** An SOS with position is delivered to all members of all groups of the sender, regardless of reciprocity and of the recipients' pause, and is sent even if the sender is paused. |
| **BR-08** | **Freshness statuses** are computed by the server from the heartbeat interval the phone itself announced (`expected interval`): **Live** — position age ≤ 2 min; **Recent** — last contact within 1.5 × expected interval; **Stale** — between 1.5 × and 4 × expected; **Offline** — beyond 4 ×. Device-reported states (Paused, Location off, No permission, Limited, Power saving, Low battery) are shown on top. *(config, spike)* |
| **BR-09** | **No backwards jumps.** The current position of a member changes only to a point with a later position time. Late points delivered from the offline buffer go to history, not to the live marker. |
| **BR-10** | **One active device per account** (MVP). Signing in on a new phone makes it the account's device; the previous device is signed out and stops sharing. |
| **BR-11** | **Operator capabilities** (see all users, request a fresh location, turn sharing on remotely, view all history) exist only when **both** the build flag (development/beta build) and the server switch are on. The Google Play build is compiled without them. All operator actions follow BR-04. |
| **BR-12** | **Limits** *(config)*: up to 20 members per group; up to 10 groups per user; up to 20 places per group; place radius 100–1000 m; an invitation is single-use and expires after 72 h; fresh-location requests: at most one push per target per 30 s and at most 10 requests per requester per target per hour. |
| **BR-13** | **No coordinates outside the product.** Crash reports, logs shipped off the device and server logs above debug level contain no coordinates, place names or tokens. |
| **BR-14** | **Retention.** Every accepted point is stored from day one (for v1.1 history). Points older than the owner's retention period are deleted: 30 days by default *(config)*; in the MVP the default applies to everyone. |

### 2.1. Visibility matrix (MVP)

What viewer V sees about member M in a shared group:

| M's state ↓ / V's state → | V shares | V paused / not sharing | Operator (dev/beta) |
|---|---|---|---|
| M shares | Position, freshness, accuracy, battery, status | Name, freshness status, battery, "resume sharing to see" | Everything |
| M paused | "Paused since/until …", last position dimmed | "Paused" | Everything + last position |
| M location off / no permission / limited | Status + last position with its age | Status | Everything |
| M sends SOS | SOS with live position | SOS with live position | Everything |
| M has no position yet | "No location yet" | "No location yet" | Device status |

## 3. Epics

| Epic | Stories | Main release |
|---|---|---|
| E1 Account and session | US-001–US-007 | MVP (US-006 — Play) |
| E2 Groups | US-010–US-017 | MVP |
| E3 Sharing | US-020–US-028 | MVP |
| E4 Map | US-030–US-035 | MVP |
| E5 Places | US-040–US-043 | MVP |
| E6 SOS | US-050–US-052 | MVP |
| E7 Alerts and notifications | US-060–US-063 | MVP |
| E8 Privacy and transparency | US-070–US-074 | MVP (US-074 — v1.1) |
| E9 History | US-080–US-084 | v1.1 |
| E10 Operator (development/beta builds) | US-090–US-095 | MVP |
| E11 Store release | US-100 | Play |
| E12 Platform | US-110–US-112 | MVP |
| E13 Future | US-130–US-133 | v2 |

---

## 4. E1 — Account and session

### US-001 Sign in with Google · MVP · Must
As a new or returning user, I want to sign in with my Google account so that I do not create another password.
- **AC1.** Given the app is installed and I am signed out, when I tap "Continue with Google" and pick an account in the system dialog, then I am signed in and my display name and photo come from that account.
- **AC2.** Given the Google sign-in fails or is cancelled, when I return to the app, then I see a clear message and can retry; no partial account is created.
- **AC3.** Given I sign in for the first time, when the account is created, then I have completed age and terms confirmation (US-007) before any location data is collected.

### US-002 Sign out · MVP · Must
- **AC1.** Given I am signed in and sharing, when I sign out, then sharing stops on this phone, the server session is revoked and the group sees me as "Offline" from that moment (with the last position's age).
- **AC2.** Given I signed out, when I open the app, then no group data from the previous session is visible.

### US-003 Stay signed in securely · MVP · Must
As a user, I want to stay signed in without re-entering credentials, and I want my session to be safe if the phone is lost.
- **AC1.** Given I signed in, when I reopen the app after days, then I am still signed in without a prompt.
- **AC2.** Given session tokens are stored on the phone, then they are kept in Android Keystore-backed storage and are never written to logs or backups.
- **AC3.** Given my session was revoked on the server (sign-out, account deletion, new device), when the phone next contacts the server, then it is signed out and sharing stops.

### US-004 Move to a new phone · MVP · Must
- **AC1.** Given I have an account, when I sign in on another phone, then that phone becomes my device (BR-10) and my group memberships, places and settings are available there.
- **AC2.** Given my old phone is still signed in, when the new phone signs in, then the old phone stops sharing and shows "Signed in on another device".

### US-005 Delete my account in the app · MVP · Must
- **AC1.** Given I am signed in, when I choose "Delete account" and confirm, then sharing stops immediately, all my sessions are revoked, and I disappear from all groups.
- **AC2.** Given my account is deleted, then my live position and all my points are deleted immediately; the remaining account records and access log are deleted within 30 days; backups age out within the backup retention period.
- **AC3.** Given I own a group with other members, when I delete my account, then ownership passes to the longest-standing admin, otherwise to the longest-standing member; a group with no other members is deleted.

### US-006 Delete my account on the web · Play · Must
- **AC1.** Given I no longer have the app, when I open the account-deletion web page and confirm with my Google account, then the same deletion as US-005 happens.
- **AC2.** The page URL is listed in the store listing and in the privacy policy.

### US-007 Confirm age and accept terms · MVP · Must
- **AC1.** Given I create an account, when I reach the confirmation step, then I must confirm that I am 13 or older and accept the terms and the privacy policy (both open as readable pages) before continuing.
- **AC2.** Given I do not confirm, then no account is created.

---

## 5. E2 — Groups

### US-010 Create a group · MVP · Must
- **AC1.** Given I am signed in, when I create a group with a name (1–40 characters), then I become its owner and can invite members.
- **AC2.** Given I am already in the maximum number of groups (BR-12), then I cannot create another and see why.

### US-011 Invite by code or link · MVP · Must
- **AC1.** Given I am owner or admin, when I tap "Invite", then I get a single-use invitation with a short human-readable code and a link that I can share through any app.
- **AC2.** Given an invitation was created, then it expires after 72 h *(config)*, can be revoked by owner/admins, and is listed as pending until used, expired or revoked.
- **AC3.** Given the group is full (BR-12), then a new invitation cannot be created.
- **AC4.** Given someone tries invitation codes repeatedly, then attempts are rate-limited per account and per IP.

### US-012 Accept or decline an invitation · MVP · Must
- **AC1.** Given I open a valid invitation, then I see the group name, who invited me, current members and the text "Members will see your location while your sharing is on; you will see theirs while you share."
- **AC2.** Given I accept, then I become a member, all members get "<name> joined", and if my sharing is off the app offers to turn it on.
- **AC3.** Given I decline, then I do not become a member and the inviter sees the invitation as declined.
- **AC4.** Given the invitation is expired, used or revoked, then I see a clear message and nothing changes.
- **AC5.** Given the app is not installed, when I open the link, then a web page explains how to install the app and enter the code.

### US-013 Be in several groups · MVP · Must
- **AC1.** Given I am a member of several groups, then each group has its own members, places and roles, and membership in one group gives no access to another.
- **AC2.** Given BR-03 (MVP), then my sharing switch and pause apply to all my groups at once, and the app says so.

### US-014 Manage roles · MVP · Must
- **AC1.** Given I am the owner, when I promote a member to admin or demote an admin, then the change applies immediately and the person is notified.
- **AC2.** Given I am an admin, then I can invite, revoke invitations, remove members (not admins, not the owner), rename the group and manage places; I cannot change roles or delete the group.
- **AC3.** Given I am a member, then I can view, subscribe to place alerts, request a fresh location and leave; management actions are not shown.

### US-015 Leave a group · MVP · Must
- **AC1.** Given I am a member or admin, when I leave, then I immediately lose access to the group and stop sharing with it; members get "<name> left".
- **AC2.** Given I am the owner and there are other members, then I must transfer ownership (US-017) or delete the group before leaving.

### US-016 Remove a member · MVP · Must
- **AC1.** Given I am owner (any member) or admin (members only), when I remove someone, then they lose access immediately (open map streams are closed) and get a notification.
- **AC2.** Given a removed person had place subscriptions in the group, then those subscriptions are deleted.

### US-017 Transfer ownership or delete a group · MVP · Must
- **AC1.** Given I am the owner, when I transfer ownership to another member, then they become owner and I become admin.
- **AC2.** Given I am the owner, when I delete the group and confirm, then all memberships, invitations, places and subscriptions of the group are deleted and members are notified.

---

## 6. E3 — Sharing

### US-020 Onboarding with prominent disclosure and permissions · MVP · Must
- **AC1.** Given I have not granted location access, when I reach the location step, then a full-screen disclosure explains that the app collects location **including in the background, when the app is closed or not in use**, to show it to my groups, for places alerts and SOS — and the system dialog appears only after I press the continue button.
- **AC2.** Given I granted location while using the app, then a second explanation leads me to the system setting "Allow all the time" (Android 11+) or shows the system dialog (Android 10).
- **AC3.** Given Android 13+, then notification permission is requested with an explanation before the location steps.
- **AC4.** Given the battery step, then "disable battery optimization", turning off the system's unused-app restrictions ("Pause app activity if unused" or its equivalent on the Android version) and phone-maker instructions are offered as recommended and skippable, with a plain explanation: without them sharing may stop on its own, and after months without opening the app Android may remove the location permission ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)).
- **AC5.** Given I quit onboarding, when I reopen the app, then it resumes at the first incomplete step.
- **AC6.** Disclosure texts exist in Ukrainian and English and are reviewed against the Google Play prominent-disclosure requirements ([policy](https://support.google.com/googleplay/android-developer/answer/9799150), [best practices](https://support.google.com/googleplay/android-developer/answer/11150561)).

### US-021 Turn sharing on and off · MVP · Must
- **AC1.** Given onboarding is complete and I am in at least one group, when I turn sharing on, then the persistent notification appears and my position starts reaching my groups.
- **AC2.** Given sharing is on, when I turn it off from the main screen or the notification (one tap), then collection stops immediately and members see "Paused" (an indefinite pause, see US-022).
- **AC3.** Given I never turned sharing on, then no location is collected and members see "Not sharing".

### US-022 Visible pause with optional timer · MVP · Must
- **AC1.** Given sharing is on, when I pause for 1 h, until a chosen time, or until I resume, then collection stops immediately, the persistent sharing notification is replaced by a quiet notification "Paused until HH:MM · Resume", and members see "Paused since HH:MM" or "Paused until HH:MM" with my last position dimmed.
- **AC2.** Given a timed pause ends, then sharing resumes automatically (the server sends an end-of-pause wake message; the phone also keeps its own alarm); if Android does not allow the restart, I get a notification "Pause ended — tap to resume" and members see "Pause ended, waiting for an update". Automatic resume is most reliable when the battery-optimization exemption is granted (US-020 AC4); the pause screen says so.
- **AC3.** Given I am paused, then fresh-location requests to me are rejected and the requester sees "Paused".

### US-023 Reciprocity · MVP · Must
- **AC1.** Given I am paused or not sharing, when I open a group map, then I see members' names, statuses and battery but no positions, and a banner "Resume sharing to see others' locations".
- **AC2.** Given I am paused, then I do not receive place alerts, and I cannot request fresh locations.
- **AC3.** Given someone sends SOS, then I see it with the position even while paused (BR-07).
- **AC4.** Server tests prove that positions are never returned to a non-sharing viewer through any endpoint or stream.

### US-024 Background sharing and persistent notification · MVP · Must
- **AC1.** Given sharing is on, when the app is in the background or the screen is locked, then points keep reaching the server at the current mode's rate.
- **AC2.** Given sharing is on, then the persistent notification shows the state (sharing / someone is viewing / SOS) and a "Pause" action.
- **AC3.** Given the phone restarts or the app is updated, then sharing resumes without opening the app (where Android allows).
- **AC4.** Given the system stopped the service and it cannot restart itself, then I get "Sharing stopped — tap to resume" and members see my status go Stale → Offline.

### US-025 Adaptive frequency and profiles · MVP · Must (profiles — Should)
- **AC1.** Given sharing is on and I am stationary, then the phone sends a heartbeat at least every 15 min *(spike)*; when moving, a position about every 60 s on foot and 30 s in a vehicle *(spike)*.
- **AC2.** Given I choose a profile — Economy, Balanced (default), Frequent — then the intervals change accordingly; values come from server configuration.
- **AC3.** Given low battery (< 15%, not charging), then intervals are increased automatically and members see "Low battery — updates less often".

### US-026 Offline buffer and resend · MVP · Must
- **AC1.** Given no network, then points are stored on the phone (up to 7 days or 10 000 points *(config)*) and sent when the network returns, the newest first.
- **AC2.** Given a batch is sent twice, then the server stores each point once.
- **AC3.** Given buffered points arrive late, then the live marker does not move backwards (BR-09).

### US-027 Limited sharing modes · MVP · Must
- **AC1.** Given only "while using the app" permission, then points are sent only while the app is open, and members see "Limited: only while the app is open".
- **AC2.** Given only approximate location, then points are marked approximate, the accuracy circle reflects it, and the self-check recommends precise location.
- **AC3.** Given location services are turned off or the permission is revoked, then the phone reports it, members see "Location off" or "No permission" with the last position's age, and I get a notification explaining how to fix it.

### US-028 Fresh location while viewing or on request · MVP · Must
- **AC1.** Given I share and open a group map, then sharing members' phones switch to frequent updates for a limited time (renewed while the map is open, capped per session *(spike)*), and their persistent notification shows "<my name> is viewing the map".
- **AC2.** Given I tap "Refresh" on a member card, then I immediately see the current known position with its age and "Requesting…"; the target phone shows "<my name> requested your location"; the request is logged in the target's access log.
- **AC3.** Given no fresh point arrives within 60 s, then I see "Could not get a fresh location" and the previous position with its true age.
- **AC4.** Given rate limits (BR-12) are exceeded, then the request is rejected with a clear message.

---

## 7. E4 — Map

### US-030 Group map · MVP · Must
- **AC1.** Given I share and open a group, then I see a map with a marker for each member who has a position, and a list of all members including those without one.
- **AC2.** Given a member's new position arrives, then the marker updates within a few seconds without reloading the map (realtime stream; fallback polling every 30 s).
- **AC3.** Given the map tiles cannot load, then the member list with statuses and position ages still works.

### US-031 Freshness and status · MVP · Must
- **AC1.** Given a member's state, then the marker and list show one status per BR-08 with text, not colour alone (for example "1 min ago", "at this place since 08:10", "no updates for 25 min", "not in contact since 09:12", "Paused until 21:00", "Location off").
- **AC2.** Given a stationary member, then the card shows both the position age ("here since 08:10") and the last contact ("checked 3 min ago").
- **AC3.** The map never interpolates or predicts movement between points.

### US-032 Accuracy · MVP · Must
- **AC1.** Given a position, then the marker shows an accuracy circle and the card shows "accuracy ± N m".
- **AC2.** Given a position flagged as low-accuracy or approximate, then it is visually distinguishable and labelled.

### US-033 Battery level · MVP · Must
- **AC1.** Given a member shares, then their latest reported battery level and charging state are shown on the card and as a small indicator in the list.

### US-034 Member card · MVP · Must (directions — Could)
- **AC1.** Given I tap a member, then the card shows name, photo, status, position age, last contact, accuracy, battery, and actions "Refresh" and "Show on map".
- **AC2 (Could).** Given a position, when I tap "Directions", then the position opens in a navigation app of my choice.

### US-035 Switch between groups · MVP · Must
- **AC1.** Given I am in several groups, then I can switch the map between groups in one tap and the app remembers the last one.

---

## 8. E5 — Places

### US-040 Manage places · MVP · Must
- **AC1.** Given I am owner or admin, when I create a place with a name, a point on the map and a radius of 100–1000 m, then it appears for all members of the group.
- **AC2.** Given I am owner or admin, then I can edit or delete places; subscribers of a deleted place are notified.
- **AC3.** Given the group already has the maximum number of places (BR-12), then I cannot add another.

### US-041 Subscribe to place alerts · MVP · Must
- **AC1.** Given I am a member, when I subscribe to a place for arrivals, departures or both, then I get alerts about any sharing member of this group crossing that place.
- **AC2.** Given I can mute alerts about a specific member for a place, then that applies only to me.

### US-042 Arrival and departure alerts · MVP · Must
- **AC1.** Given I am subscribed and sharing, when a member's points show they entered or left the place, then I get "<name> arrived at <place> · HH:MM" or "<name> left <place> · HH:MM" with the estimated crossing time.
- **AC2.** Given positions with poor accuracy near the boundary, then no alert is produced until the state is confirmed (no flapping in and out). *(rule details in the places spec)*
- **AC3.** Given the member or I am paused, then no alert is produced (BR-02).

### US-043 Know who gets alerts about me · MVP · Should
- **AC1.** Given someone subscribes to a place in my group, then I get a quiet notification "<name> will get alerts when you arrive at or leave <place>".
- **AC2.** Given the transparency screen (US-070), then all current subscriptions affecting me are listed.

---

## 9. E6 — SOS

### US-050 Send SOS · MVP · Must
- **AC1.** Given I press and hold the SOS button (≈ 2 s *(config)*), then SOS is sent; a short tap does nothing but shows a hint.
- **AC2.** Given SOS is sent, then the last known position is sent immediately, followed by frequent updates for 15 min *(config)* — even if my sharing was paused (BR-07), and the app tells me so.
- **AC3.** Given the SOS screen, then it offers "Call 112" (opens the dialer, no call permission needed) and "Cancel SOS".
- **AC4.** Given no network, then SOS is queued, I see "Not delivered yet", and it is sent automatically when the network returns.

### US-051 End SOS · MVP · Must
- **AC1.** Given SOS is active, when I tap "I am safe" / "Cancel" or 15 min pass, then frequent updates stop, my previous sharing state (including pause) is restored, and recipients get "<name>: SOS ended".

### US-052 Receive SOS · MVP · Must
- **AC1.** Given a member of any of my groups sends SOS, then I get a high-importance notification "<name> sent SOS" with the position age, regardless of my pause (BR-07).
- **AC2.** Given I open it, then I see the live position on the map with frequent updates.
- **AC3.** Given the SOS notification channel is disabled in system settings, then the self-check (US-072) flags it as red.

---

## 10. E7 — Alerts and notifications

### US-060 Low battery of a member · MVP · Must
- **AC1.** Given a sharing member's battery drops below 15% *(config)* while not charging, then members who keep this alert on get one notification per discharge episode.

### US-061 No updates from a member · MVP · Must
- **AC1.** Given a member's status changes to Offline (BR-08), then members who keep this alert on get one quiet notification "No updates from <name> since HH:MM", once per episode.
- **AC2.** Given the member comes back, then no "back online" notification is sent; the map updates silently.

### US-062 Group event notifications · MVP · Must
- **AC1.** Given an invitation to me, a member joining or leaving, my removal, or my role change, then I get a notification.

### US-063 Notification preferences · MVP · Must
- **AC1.** Given a group, then I can turn on/off: low-battery alerts, no-updates alerts, membership events; place alerts are controlled by subscriptions (US-041).
- **AC2.** SOS notifications cannot be turned off in the app (only by leaving the group); the self-check warns if the system channel is off.

---

## 11. E8 — Privacy and transparency

### US-070 "Who sees what about me" · MVP · Must
- **AC1.** Given I open the screen, then I see: my groups and sharing state, who in each group can see my position now (sharing members, BR-02), place subscriptions that affect me, the latest fresh-location requests and operator actions (US-071).
- **AC2.** Given v1.1, then the screen also shows who can see my history.

### US-071 Access log · MVP · Must
- **AC1.** Given someone requests my fresh location or the operator acts on my account, then an entry with who, what and when appears in my access log and stays 90 days *(config)*.
- **AC2.** Given the access log, then only I (and the operator in development/beta builds) can read it.

### US-072 Reliability self-check · MVP · Must
- **AC1.** Given I open the self-check, then it shows the status of: location permission (all the time, precise), notification permission and SOS channel, battery optimization, the system's unused-app restrictions, phone-maker background restrictions (where detectable, otherwise instructions), power saving, location services, last successful upload.
- **AC2.** Given an item is not OK, then a "Fix" button opens the matching system screen, with phone-maker-specific instructions where available.
- **AC3.** Given a critical problem appears while sharing (permission revoked, location off), then I get a notification leading to the self-check.
- **AC4.** Given the self-check results, then a summary (no coordinates) is reported to the server for the device status shown to the operator.

### US-073 Privacy policy and terms in the app · MVP · Must
- **AC1.** Given any screen of settings, then the privacy policy and terms are reachable in two taps and open as web pages at stable URLs.

### US-074 Export my data · v1.1 · Should
- **AC1.** Given I request an export, then I receive a file with my account data, memberships, places I created and my points (JSON; tracks also as GPX).

---

## 12. E9 — History (v1.1)

### US-080 View my history · v1.1 · Must
- **AC1.** Given I open "My history" and pick a day, then I see my track with stops detected and GPS noise filtered.

### US-081 Share my history · v1.1 · Must
- **AC1.** Given history is visible only to me by default, when I share it with a group or with a person, then they can see it until I stop sharing it; the transparency screen lists who can see it.

### US-082 Retention period · v1.1 · Must
- **AC1.** Given the default retention of 30 days, when I change it within the allowed range *(config)*, then older points are deleted at the next cleanup.

### US-083 Delete my history · v1.1 · Must
- **AC1.** Given I delete one day or all history, then the points are deleted on the server and are no longer visible to anyone, including people I shared history with.

### US-084 Per-group sharing switch · v1.1 · Should
- **AC1.** Given I am in several groups, then I can turn sharing on/off or pause per group; reciprocity applies per group.

---

## 13. E10 — Operator (development and beta builds only)

### US-090 Operator disclosure · MVP · Must
- **AC1.** Given a development/beta build, then onboarding includes a screen listing operator capabilities; the user must confirm it before continuing.
- **AC2.** Given the beta, then each participant receives the same information before installing (beta instructions).

### US-091 Operator overview · MVP · Should
- **AC1.** Given operator mode is enabled (BR-11), then the operator can see in the back office all users, groups, device statuses, live positions and history.
- **AC2.** Given the operator watches live positions, then each affected person gets one access-log entry per day "The operator viewed live locations" and no phone notification; opening a person's history is an explicit action and notifies (BR-04).

### US-092 Operator requests a fresh location · MVP · Should
- **AC1.** Given operator mode, when the operator requests a user's location, then the same flow as US-028 runs, the target sees "The operator requested your location" and the log records it.

### US-093 Operator turns sharing on remotely · MVP · Could
- **AC1.** Given operator mode and a user who completed onboarding with background permission, when the operator turns sharing on, then the phone starts sharing where Android allows it, shows the persistent notification and "Sharing was turned on by the operator"; the action is logged.
- **AC2.** Given Android does not allow a background start, then the user gets a notification to resume, and nothing is started silently.

### US-094 Operator capabilities can be removed · MVP · Must
- **AC1.** Given a Google Play build, then the app contains no operator code paths or screens, and the server rejects operator actions for it.
- **AC2.** Given the server switch is off, then operator endpoints are disabled for all builds.

### US-095 Remote configuration · MVP · Should
- **AC1.** Given the back office, when the operator publishes new engine parameters (intervals, thresholds) or the map style URL, then phones apply them at the next contact without an app update.

---

## 14. E11 — Store release

### US-100 Share only on request · Play · Must
- **AC1.** Given I choose "Only on request", then my phone sends no periodic positions, only a heartbeat without coordinates; members see "On request".
- **AC2.** Given a member requests my location, then my phone shows "<name> requested your location", sends one fresh point and returns to the economical state; the request is logged.
- **AC3.** Given I switch back to "Always", then periodic sharing resumes.

Notes for the on-request specification:
- While a person is in "Only on request", no periodic points reach the server, so arrival/departure alerts about this person do not work; the place screen and the transparency screen say so.
- By BR-02 a person in "Only on request" counts as sharing and sees other members live while being seen only on request. This asymmetry softens principle P4 ("no invisible watchers"); revisit it in the on-request specification (for example: viewers in "Only on request" also see others only on request, or others see that this viewer is in "Only on request").

(US-006 web account deletion is also part of this release.)

---

## 15. E12 — Platform

### US-110 Localization · MVP · Must
- **AC1.** Given the device language is Ukrainian, then the app is in Ukrainian; for any other language it is in English.
- **AC2.** Given Android 13+, then I can choose the app language in system settings.
- **AC3.** All texts, including disclosures, notifications and server-generated alerts, come from translatable resources; adding a language requires no code changes.

### US-111 Accessibility · MVP · Must
- **AC1.** Given system font size up to 200%, then key information (names, statuses, ages, SOS) is readable without clipping.
- **AC2.** Given TalkBack, then markers, statuses and buttons have meaningful labels.
- **AC3.** Statuses are conveyed by text or icon, not by colour alone; touch targets are at least 48 dp.

### US-112 Unsupported devices · MVP · Must
- **AC1.** Given Google Play services are missing or unusable, then the app shows "This device is not supported" with the reason and does not start onboarding.

---

## 16. E13 — Future (v2), short form

| ID | Story | Notes |
|---|---|---|
| US-130 | Share only an approximate area with a selected group | Idea common to the category and to Android's own approximate location |
| US-131 | Sign in with email and a one-time code | Needed for future iOS/web clients and as a fallback |
| US-132 | Automatic trip detection and trip summaries | Builds on history |
| US-133 | Home-screen widget with the group's statuses | — |

## 17. Assumptions

Product decisions taken by default in this blueprint; each can be changed during review.

| # | Assumption |
|---|---|
| A1 | Working product name "Locator" until branding. |
| A2 | Invitations are single-use, valid 72 h; any owner/admin can create them; no approval step after the invitee accepts. |
| A3 | Roles: owner (one per group), admin, member — rights as in US-014. |
| A4 | One active device per account (BR-10). |
| A5 | Pause options: 1 h, until a chosen time, until I resume. |
| A6 | SOS: press-and-hold, 15 min of frequent updates, goes to all members of all the sender's groups, works while paused. |
| A7 | Low-battery threshold 15%; "no updates" alert on transition to Offline; both on by default, quiet notifications, can be turned off per group. |
| A8 | Explicit fresh-location requests are logged; simply viewing the map is shown live in the target's persistent notification but not logged. The operator's passive live overview is logged once per person per day without a phone notification (BR-04, owner's decision in the blueprint review). |
| A9 | Account deletion: location data immediately, other records within 30 days. |
| A10 | Limits in BR-12. |

## 18. Out of scope

Child accounts and anyone under 13; parental-control or employee-monitoring features; hidden modes; ads, data sales, third-party analytics; monetization; SMS-based features; iOS (until a later decision); devices without Google Play services (until a later decision).

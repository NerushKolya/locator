# Permissions rationale

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [data inventory](data-inventory.md) · [store compliance](store-compliance.md) · [Android architecture](../architecture/android.md) · [PRD](../product/prd.md) (US-020, US-027, US-072)

Which Android permissions the app declares, why each is needed, when it is requested, what the person reads before the system dialog, and what is written in the Google Play declarations. Texts here are the English source; the Ukrainian texts live in the app's string resources and are translated from these. Final wording is polished in the design stage, but the mandatory elements marked **(required)** must stay.

## 1. Rules

1. Ask only for what a visible feature needs, at the moment the person needs it, after explaining why.
2. The system dialog appears only after an explicit tap on the explanation screen. Leaving the screen is not consent.
3. Every step can be declined. The app then works in a clearly named limited mode and never nags on every launch; the reliability self-check is the place to fix it later.
4. Foreground and background location are requested in two separate steps, foreground first ([Android guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)).
5. Explanations never imitate system dialogs and are not mixed with unrelated disclosures ([Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)).

## 2. Declared permissions

| Permission | Kind | Why | When asked | Without it |
|---|---|---|---|---|
| `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | Runtime | Read the phone's location to share it with the person's groups. Requested together, as Android 12+ requires | Onboarding step "Location", after the prominent disclosure | No sharing. With approximate only: positions are marked approximate with a large accuracy circle |
| `ACCESS_BACKGROUND_LOCATION` | Runtime, separate step (system settings on Android 11+) | Keep sharing when the app is closed; restart sharing after a reboot, after an update, and on a fresh-location request. Android gives a location service started from the background access to location only with this permission | Onboarding step "All the time", after foreground location was granted | Limited mode "only while the app is open": sharing does not survive a reboot or a process kill and cannot answer requests |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Install-time | Run the sharing service with its persistent notification (service type `location`) | — | — |
| `POST_NOTIFICATIONS` (Android 13+) | Runtime | Show the persistent sharing notification, SOS and place alerts, fresh-location requests, "Sharing stopped" | Onboarding, before the location steps | Alerts are not shown; the self-check marks it as a problem. Android may still list the running service in its task manager |
| `RECEIVE_BOOT_COMPLETED` | Install-time | Resume sharing after a reboot or app update without opening the app | — | — |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Install-time; opens a system dialog | Offer the person to exempt the app from battery optimization so that the system does not stop sharing. Recommended, skippable step | Onboarding step "Battery" and the self-check | Sharing may be interrupted on some phones; the self-check explains |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Install-time | Talk to the server; upload buffered points when the network returns | — | — |
| `WAKE_LOCK`, `com.google.android.c2dm.permission.RECEIVE` | Install-time, added by libraries | Push messages and scheduled background work | — | — |

## 3. Permissions the app does not request

| Not requested | Reason |
|---|---|
| `ACTIVITY_RECOGNITION` | Motion is detected from an anchor geofence and displacement. Requested only if the spike shows that start of movement is otherwise detected too late; that would add one more onboarding step |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | The end of a timed pause uses an inexact alarm |
| `CALL_PHONE` | "Call 112" opens the dialer with the number filled in |
| Contacts, SMS, call log, camera, microphone, storage, calendar | No feature needs them; invitations are shared through the system share sheet |
| `SYSTEM_ALERT_WINDOW`, accessibility services, device admin, `QUERY_ALL_PACKAGES` | Not needed; typical of stalkerware |
| `isMonitoringTool` manifest flag | The app is not a monitoring tool: every person shares only their own location by their own action |

## 4. Onboarding texts

Placeholders: `{app}` — product name; `{setting}` — the label returned by Android for the "Allow all the time" option.

### 4.1. Notifications (Android 13+)

> **Stay informed**
> {app} shows a notification whenever your location is being shared, and alerts you about SOS signals, arrivals and requests for your location.
> [Allow notifications] [Not now]

### 4.2. Prominent disclosure — location

Full screen, shown before any location permission dialog. **(required)** elements: the word "location"; the statement that collection happens in the background / when the app is closed or not in use; every feature that uses it; an explicit action to continue ([policy](https://support.google.com/googleplay/android-developer/answer/9799150), [best practices](https://support.google.com/googleplay/android-developer/answer/11150561)).

> **Your location, shared with your groups**
> **{app} collects location data to show your position to members of your groups, to send arrival and departure alerts for your groups' places, and to send your position with an SOS — even when the app is closed or not in use.**
> Sharing starts only when you turn it on. A notification is always visible while it is on, and you can pause or stop in one tap.
> We do not use your location for advertising and do not sell or pass it to anyone outside your groups.
> [Continue] [Not now]

The system dialog follows "Continue". If the person chooses approximate location, the result screen explains that members will see a large circle instead of a point.

### 4.3. Background location ("Allow all the time")

Shown after foreground location was granted.

> **Keep sharing when the phone is in your pocket**
> To share your location when {app} is closed, and to resume after the phone restarts, choose **"{setting}"** on the next screen.
> Without it, your groups see you only while the app is open on your screen.
> Android will remind you from time to time that {app} has this access — that is expected.
> [Open settings] [Not now]

### 4.4. Battery (recommended)

> **Help your phone keep sharing**
> Some phones stop apps in the background to save battery. Allow {app} to run without battery restrictions so your groups do not lose you.
> {app} adapts how often it reports: rarely when you are still, more often when you move.
> [Allow] [Skip]

Followed by the unused-app step (no permission involved; the button opens the system screen through `IntentCompat.createManageUnusedAppRestrictionsIntent()`; the step is skipped when `PackageManagerCompat.getUnusedAppRestrictionsStatus()` reports the restriction already off or not available — [app hibernation](https://developer.android.com/topic/performance/app-hibernation)):

> **Keep sharing even if you rarely open {app}**
> If you do not open {app} for a few months, Android may pause it and remove its location permission. Your groups would stop seeing you without you noticing.
> Turn off "{setting}" for {app} so sharing keeps working.
> [Open settings] [Skip]

`{setting}` is the system's name on the phone's Android version: "Pause app activity if unused" (Android 13+), "Remove permissions and free up space" (Android 12), "Remove permissions if app isn't used" (Android 11; on Android 10 — in Google Play Protect).

Then, on known phone makers, step-by-step instructions (auto-start, recent-apps lock, vendor battery settings) with a button that opens the right settings screen where one exists.

### 4.5. Operator disclosure (internal flavor only)

> **This is a test build**
> During the beta the operator of the service (the developer) can: see the location and history of every participant, request a fresh location, and turn sharing on remotely.
> A fresh-location request, a remote turn-on and a look at your history are shown on your phone. Every access, including the operator simply watching the live map, is recorded in "Who sees what about me". The sharing notification is never hidden.
> These capabilities do not exist in the version published on Google Play.
> [I understand and agree] [Cancel]

### 4.6. Invitation consent

Shown before accepting an invitation; its version is stored with the consent record.

> **Join "{group}"?**
> Members will see your location while your sharing is on. You will see theirs while you share yours.
> You can pause sharing or leave the group at any time.
> [Join] [Decline]

### 4.7. Result screen

Lists what was granted and, for each missing item, the consequence in one line: "Location: only while the app is open", "Approximate location: members see a large circle", "Notifications off: you will not get SOS and place alerts", "Battery optimization on: sharing may stop on this phone". Each line has a "Fix" button.

## 5. Notifications that make activity visible

| Situation | Notification text (English source) |
|---|---|
| Sharing is on (persistent) | "Sharing your location with your groups" · action "Pause" |
| Someone has the map open | "{name} is viewing the map" (persistent notification text changes) |
| Fresh-location request | "{name} requested your location" |
| Request received while the service was stopped and cannot start | "Open {app} to share your location" |
| Service revived after silence | "Location sharing was restored" |
| Service stopped and cannot restart | "Sharing stopped — tap to resume" |
| Sharing paused (replaces the persistent notification; quiet, ordinary notification) | "Paused until {time}" · action "Resume" |
| Timed pause ended, restart not allowed | "Pause ended — tap to resume" |
| New subscription concerning the person | "{name} will get alerts when you arrive at or leave {place}" |
| Operator request (internal flavor) | "The operator requested your location" |
| Operator turned sharing on (internal flavor) | "Sharing was turned on by the operator" |
| Operator opened the person's history (internal flavor) | "The operator viewed your location history" |
| Operator watches the live map of all users (internal flavor) | No notification; one entry per day in "Who sees what about me" (BR-04) |
| SOS active on the sender's phone | "SOS is active — your location is sent to your groups" · action "I am safe" |

## 6. Texts for Google Play Console

Drafts for the store release; checked against the current forms when submitting.

### 6.1. Background location declaration

One feature is declared, as the form requires ([policy](https://support.google.com/googleplay/android-developer/answer/9799150)).

- **Core feature:** "Live location sharing between members of a family group. A member turns sharing on in the app; their position is then shown on the group map to the other members, also while the app is closed, until the member pauses or stops sharing."
- **Why background access is needed:** "The purpose of the app is to let family members see each other's current location during the day without opening the app first (phone in a pocket, screen off). Without background access the position would only update while the app is on screen, which defeats the feature and the safety value of place alerts and SOS that depend on the same location stream."
- **User benefit:** personal safety and peace of mind for family members who agreed to share with each other.
- **Video (30 s or less):** the prominent disclosure → system permission dialogs → the user turns sharing on → the persistent notification → the app goes to the background → the position updates on another member's map.

### 6.2. Foreground service declaration (type `location`)

- **Use case:** "Background location updates: user-initiated location sharing" ([declaration help](https://support.google.com/googleplay/android-developer/answer/13392821)).
- **Description:** "The user starts location sharing in the app. A foreground service with a persistent notification collects the device location and uploads it so that members of the user's groups can see it. The user can pause from the notification at any time."
- **Impact if interrupted or deferred:** "Group members would see an outdated position without knowing it; arrival alerts and SOS position updates would be late or missing."

### 6.3. Battery-optimization exemption

Justification if asked: the app is a family safety app (live location for family members, SOS); the Android documentation lists safety apps for users and their families as an acceptable case for the exemption request ([Doze and standby](https://developer.android.com/training/monitoring-device-state/doze-standby)). If a reviewer disagrees, the direct request is replaced by a link to the system settings list.

## 7. Change control

Any change to a permission, to the texts in §4.2–§4.6 (including the unused-app step in §4.4), or to what a feature does with location requires updating this document, the data inventory, the privacy policy and, if published, the store declarations in the same change. The version of each consent text is stored with the person's consent record.

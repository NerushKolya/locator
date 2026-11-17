# Glossary

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [PRD](prd.md) · [vision](vision.md)

One vocabulary for product documents, specifications, code and the interface. The **Code term** column is the name used in the domain model and API (the ubiquitous language); architecture documents use the same names. User-facing wording is decided in the design stage and translated; it does not have to match the code term.

## 1. People and groups

| Term | Meaning | Code term |
|---|---|---|
| **User** | A person with an account (signed in with Google). 13 or older. | `User` |
| **Device** | The phone through which a user shares and views. One active device per account in the MVP. | `Device` |
| **Group** | A small set of people (family, friends) who share locations with each other. A user can be in several groups. | `Group` |
| **Membership** | The fact that a user belongs to a group, with a role. | `Membership` |
| **Owner** | The one member who controls the group: roles, deletion, ownership transfer. | `Role.OWNER` |
| **Admin** | A member who can invite, remove members, rename the group and manage places. | `Role.ADMIN` |
| **Member** | A regular participant: sees, subscribes to alerts, requests fresh locations. | `Role.MEMBER` |
| **Invitation** | A single-use, time-limited offer to join a group, shared as a short code and a link. | `Invitation` |
| **Operator** | The service operator (super-admin). Has extra capabilities only in development and beta builds. | `Operator` |

## 2. Sharing

| Term | Meaning | Code term |
|---|---|---|
| **Sharing** | Sending one's own location to one's groups. Turned on only by the person. | `Sharing` |
| **Sharing state** | `Active` (sharing), `Paused` (temporarily or indefinitely stopped by the person), `Not started` (never turned on). | `SharingState` |
| **Sharing mode** | How an active person shares: `Always` (periodic, adaptive) or `On request` (only when someone asks; Play release). | `SharingMode` |
| **Pause** | A visible stop of sharing, with an optional end time. Others see "Paused since/until …". | `Pause` |
| **Consent** | The person's explicit agreement to share with a group, given by accepting an invitation and turning sharing on. Withdrawn by pausing, leaving or deleting the account. | `Consent` |
| **Reciprocity** | Rule: in a group you see others' positions only while you share yours with that group. | — (business rule BR-02) |
| **Frequency profile** | `Economy`, `Balanced` (default), `Frequent` — scales how often the phone reports. | `FrequencyProfile` |
| **Engine mode** | The location engine's internal state: stationary, moving on foot, moving in a vehicle, active view, SOS, on request, paused; low battery modifies intervals. | `EngineMode` |
| **Active view** | Temporary frequent reporting while someone in the group has the map open or asked for a fresh location. Time-limited ("lease"). | `ActiveView` |
| **Fresh location request** | An explicit request for a new point from a member's phone. Always visible on the target phone and logged. | `LocationRequest` |
| **Limited sharing** | Sharing with reduced permissions: only while the app is open, or approximate only. | `SharingLimitation` |

## 3. Positions and freshness

| Term | Meaning | Code term |
|---|---|---|
| **Point** | One recorded location: coordinates, accuracy, time, optional speed/bearing, battery, flags. Has a unique id generated on the phone. | `LocationPoint` |
| **Position** | A member's current position shown on the map: the latest point by position time. | `CurrentPosition` |
| **Accuracy** | Radius in metres within which the true location probably is. Shown as a circle. | `accuracyM` |
| **Position time** | When the point was recorded, computed by the server from the point age. | `fixedAt` |
| **Position age** | How long ago the member was at the shown position. | — |
| **Heartbeat** | A short regular "I am alive" message from the phone, with or without a point. | `Heartbeat` |
| **Last contact** | When the phone last talked to the server (any upload or heartbeat). | `lastSeenAt` |
| **Expected interval** | How soon the phone promised to report again; freshness thresholds are relative to it. | `nextReportInS` |
| **Freshness status** | `Live`, `Recent`, `Stale`, `Offline` — computed by the server (BR-08). | `Freshness` |
| **Device status** | What the phone reports about itself: location off, no permission, limited, power saving, low battery, app version. | `DeviceStatus` |
| **Offline buffer** | Points stored on the phone until the server confirms receipt. | `PointBuffer` |
| **Idempotent upload** | Sending the same points again does not create duplicates. | — |
| **History** | Stored points of one person over days; visible to the owner by default (v1.1). | `History` |
| **Retention** | How long points are kept before automatic deletion (30 days by default). | `RetentionPolicy` |
| **Track / stop** | A line through consecutive points / a place where the person stayed for a while (v1.1). | `Track`, `Stop` |

## 4. Places and alerts

| Term | Meaning | Code term |
|---|---|---|
| **Place** | A named circle (centre + radius) belonging to a group, for example "Home". Created by owner/admins. | `Place` |
| **Place subscription** | A member's choice to get arrival and/or departure alerts for a place. | `PlaceSubscription` |
| **Arrival / departure** | A member's points moved into / out of a place, as detected by the server. | `PlaceTransition` |
| **SOS** | An emergency signal from a member to all members of all their groups, with position and frequent updates for a limited time. | `Sos` |
| **Alert** | A notification caused by another member's state: place transition, SOS, low battery, no updates. | `Alert` |
| **Low-battery alert** | Sent once per discharge when a member's battery drops below the threshold. | `LowBatteryAlert` |
| **No-updates alert** | Sent once when a member's freshness becomes Offline. | `NoUpdatesAlert` |

## 5. Privacy and transparency

| Term | Meaning | Code term |
|---|---|---|
| **"Who sees what about me"** | The screen listing everyone and everything that can see or is notified about the user. | `TransparencyView` |
| **Access log** | A per-person record of fresh-location requests and operator actions concerning them; readable by that person. | `AccessLogEntry` |
| **Reliability self-check** | The screen that checks permissions, battery and phone-maker settings that affect sharing, with links to fix them. | `SelfCheck` |
| **Prominent disclosure** | A screen required by Google Play that explains, before the system permission dialog, that the app uses location in the background and why. | — |
| **Data safety** | The Google Play form where the developer declares which data is collected and why. | — |
| **Account deletion** | Removal of the account and its data, available in the app and on the web. | `AccountDeletion` |

## 6. Builds and releases

| Term | Meaning |
|---|---|
| **Development/beta build** | Build for development and the family beta; may include operator capabilities, disclosed to participants. |
| **Store build** | Build published on Google Play; compiled without operator capabilities. |
| **MVP** | The first release used daily by the family (family beta). |
| **v1.1, Play, v2, Later** | Next releases — see [feature matrix](feature-matrix.md). |
| **Spike** | A short experiment on real phones that answers a technical question (here: does background sharing work reliably and within the battery target). |

## 7. Android terms used in product documents

Short explanations for readers new to Android.

| Term | Meaning |
|---|---|
| **Foreground service** | A part of the app that keeps running in the background and must show a persistent notification. Location sharing runs in one of type "location". |
| **Persistent notification** | The notification Android shows while a foreground service runs. We never hide it. |
| **Background location** | Location access while the app is not on screen. Requires the "Allow all the time" permission. |
| **Precise / approximate location** | Permission levels: exact position, or a position accurate to roughly a city block or more. |
| **Battery optimization** | Android's restriction of background work for apps; the user can exempt an app. |
| **Phone-maker restrictions** | Additional background-killing features of some manufacturers' Android versions. |
| **Doze** | Deep sleep when the phone lies still with the screen off; background work is batched into short windows. |
| **Power saving** | A mode that limits background work and location to save battery. |
| **Push (FCM)** | Google's channel for messages from the server to the phone; used to deliver alerts and wake the app for a fresh location. |
| **Force stop** | Stopping an app from system settings; after it the app gets nothing until the user opens it again. |
| **Google Play services** | Google's system components on the phone; required by the app (location provider, push, sign-in). |

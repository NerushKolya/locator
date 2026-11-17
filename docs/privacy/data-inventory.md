# Data inventory

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [permissions rationale](permissions-rationale.md) · [store compliance](store-compliance.md) · [data model](../architecture/data-model.md) · [security](../architecture/security.md) · [PRD](../product/prd.md)

What personal data the product processes, why, where it lives, how long, and who can see it. This document is the source for the privacy policy and for the Google Play Data safety form. It is an engineering inventory, not legal advice; the privacy policy is reviewed against applicable law before the public release.

## 1. Principles

1. **Purpose limitation.** Data is used only to run the service for the person's groups. No advertising, no sale or transfer to data brokers, no third-party analytics (product principle P6).
2. **Minimization.** The app requests only the permissions the features need, and the server stores only what a feature reads.
3. **Consent of the located person.** Location is collected only while the person shares; consent is recorded and can be withdrawn in one tap.
4. **No coordinates outside the product.** Logs, error reports and push messages carry no coordinates, place names or tokens (BR-13).
5. **Limited retention.** Every category has a retention period and an automatic deletion mechanism.
6. **Transparency.** The person can see who sees what about them, and who asked for their location.

## 2. Inventory

Numbers marked *(config)* are configuration defaults.

### 2.1. Account and device

| Data | Source | Purpose | Stored in | Retention | Visible to |
|---|---|---|---|---|---|
| Google account identifier (`sub`) | Google ID token | Recognize the account at sign-in | `users` | Account lifetime + 30 days | Nobody (never returned by the API) |
| E-mail address | Google ID token | Account identification for support and web deletion | `users` | Account lifetime + 30 days | The person; operator |
| Display name, profile photo URL | Google ID token | Show the person to members of their groups | `users` | Account lifetime + 30 days | Members of shared groups; a person previewing an invitation to that group; operator |
| Interface language | App | Language of web pages and future server-generated texts | `users` | Account lifetime | The person |
| Age and terms confirmation (time, version) | The person | Evidence of confirmation | `users`, `consent_records` | Account lifetime + 30 days | Operator |
| Installation identifier (random, app-generated) | App | Bind a session to one device | `devices` | Until the device is replaced + 30 days | Operator |
| Phone maker, model, Android version, app version, build flavor | App | Phone-maker instructions, diagnostics, operator-capability check | `devices` | Same | Operator |
| Push token | Firebase SDK on the phone | Deliver push messages to this device | `devices`; transient in the outbox | Until replaced or account deletion | `delivery` service; FCM |
| Session records (hash of refresh token, times, revoke reason) | Server | Keep the person signed in; revoke | `sessions` | Until expiry or revocation + 30 days | Operator (no token values) |

The server does not store IP addresses in the database. IP addresses appear in web-server access logs (kept 14 days *(config)*) and in short-lived rate-limit counters.

### 2.2. Location

| Data | Source | Purpose | Stored in | Retention | Visible to |
|---|---|---|---|---|---|
| Current position (coordinates, accuracy, time, optional speed and bearing, flags) | Phone, while sharing | Group map | `current_positions` | Until replaced; deleted with the account | Members of shared groups who share themselves (reciprocity); all members of the person's groups during the person's SOS; operator in development/beta builds while operator mode is on |
| History points (same fields + altitude, engine mode, battery) | Phone, while sharing | Stored for the history feature (v1.1); input for place detection | `location_points` | 30 days *(config)*, then deleted | MVP: nobody but the operator in development/beta builds. v1.1: the person; people the person explicitly shares history with |
| Precision level (precise or approximate) | Phone | Honest display of accuracy | With points and device status | As above | As the position |
| Offline buffer | Phone | Deliver points recorded without network | Phone only (Room) | Until confirmed by the server; at most 7 days or 10 000 points | Nobody |

Location is **not** collected: before the person turns sharing on; while paused; after sign-out; in the "only on request" mode except when a request arrives (store release).

### 2.3. Groups, places, alerts

| Data | Source | Purpose | Stored in | Retention | Visible to |
|---|---|---|---|---|---|
| Group name, membership, role, join time | The person, group admins | Group function | `groups`, `memberships` | Until leaving or group deletion | Group members; operator |
| Invitations (code, creator, status, who used it) | Group admins | Joining a group | `invitations` | 30 days after completion | Group owner and admins |
| Places (name, centre, radius) | Group owner and admins | Arrival and departure alerts | `places` | Until deleted with the place or the group | Group members; operator |
| Place subscriptions and mutes | The subscriber | Who gets alerts about whom | `place_subscriptions` | Until unsubscribed or leaving | The subscriber; every member the subscription concerns (transparency screen) |
| Place presence and transitions (arrival/departure, estimated time) | Derived on the server from points | Alerts | `place_presence`, `place_transitions` | As history points | Recipients of the alert (subscribers who share); operator in development/beta |
| SOS episodes (start, end, reason) | The person | Emergency notification | `sos_events` | As history points | All members of all the sender's groups |
| Low-battery and no-updates episodes | Derived | One alert per episode | `alert_episodes` | Until the episode ends + 30 days | Members who keep the alert on |
| Notification preferences | The person | Respect choices | `notification_preferences` | Until leaving the group | The person |

### 2.4. Sharing state, device status, transparency

| Data | Source | Purpose | Stored in | Retention | Visible to |
|---|---|---|---|---|---|
| Sharing state, pause times, mode, frequency profile | Phone (the person's own actions) | Show "Paused since/until", apply reciprocity | `sharing_settings` | Account lifetime | Group members (state and pause times); operator |
| Consent records (kind, text version, granted, withdrawn) | The person's actions | Evidence of consent and withdrawal | `consent_records` | Account lifetime + 30 days | Operator; the person (export, v1.1) |
| Device status: last contact, expected interval, engine mode, battery level and charging, location on/off, permission level, power saving, battery-optimization exemption | Phone | Freshness statuses, honest reasons for silence, alerts, diagnostics | `device_statuses` | Latest value only; deleted with the device | Group members see: freshness, last contact, battery, and the flags of PRD §2.1; operator sees all |
| Reliability self-check summary (statuses only) | Phone | Operator diagnostics during the beta | `device_statuses` | Latest value only | The person; operator |
| Fresh-location requests | Requester | Fulfil the request; rate limits | `location_requests` | 7 days | Requester (own requests) |
| Access log (who requested or viewed, when, outcome; no coordinates) | Server | Transparency for the located person | `access_log` | 90 days *(config)* | The located person; operator in development/beta |
| Engine journal (mode changes, starts, stops, upload results; no coordinates) | Phone | Self-check and debugging | Phone only | Ring buffer | The person |

### 2.5. Operational data

| Data | Purpose | Where | Retention | Contains location? |
|---|---|---|---|---|
| Structured service logs | Operation and debugging | Host (container logs with rotation) | 14 days *(config)* | No (allow-listed fields) |
| Web-server access logs (IP address, path, status) | Security, abuse handling | Host | 14 days *(config)* | No; invitation codes in paths are masked |
| Error reports | Fixing defects | Error-tracking service | 30 days (service default) | No (scrubbing filter with tests) |
| Database backups | Disaster recovery | Two storage locations, encrypted before upload | 7 daily + 4 weekly | Yes, encrypted; private key offline |
| Outbox events | Delivery to streams and push | `outbox` | 24 h | Stream position events only |
| Operator session audit | Accountability of the operator | `backoffice` schema | 1 year *(config)* | No |

## 3. Recipients outside the project

| Recipient | Role | What it receives | What it does not receive |
|---|---|---|---|
| Google Identity (Sign in with Google) | Identity provider | The fact of sign-in from this app | Location, groups |
| Firebase Cloud Messaging (Google) | Processor: message delivery | Push token, message type, internal identifiers | Coordinates, names, place names |
| Hosting provider (virtual machine, EU region) | Processor: infrastructure | Everything stored on the server, as encrypted volumes and network traffic | — |
| Backup storage providers | Processor: storage | Encrypted backup files | Decryption key |
| Error-tracking service | Processor: diagnostics | Stack traces, app and OS version, phone model, random installation id | Coordinates, tokens, e-mail, names, place names |
| Map tile provider | Independent service contacted by the phone | The phone's IP address and the map tiles requested (the area being viewed) | Account identity, members' coordinates as such |
| Firebase App Distribution (beta only) | Processor: test distribution | Testers' e-mail addresses | Location |
| Tunnel provider (field tests before the server exists) | Processor: network transport | Encrypted traffic to the developer's machine | Readable content |

Notes:

- **Map tiles.** The area a person looks at on the map usually includes their group's positions, so tile requests reveal an approximate region to the tile provider, together with an IP address. This is inherent to any hosted map. The provider's logging practice must be checked before the public release; self-hosted tiles (plan B of ADR-018) remove this recipient.
- **Members of the person's groups** are not "third parties" in the sense of the store form: the person shares with them by their own action, and the product shows exactly who they are.
- **No other recipients.** No advertising networks, analytics providers or data brokers; this is a product principle, not a setting.
- **Legal requests.** Data may be disclosed when the law requires it; the policy says so. In the store release the operator has no interface to positions, so any such disclosure is a manual database operation that is recorded.

## 4. What group members see about each other

Summary of PRD §2.1 from the data point of view.

| Viewer | Sees about another member |
|---|---|
| Shares with the group | Name, photo, role; sharing state and pause times; freshness status and last contact; battery; device flags (location off, no permission, limited, power saving, low battery); current position with accuracy and time; last position (dimmed) of a paused or silent member |
| Paused or not sharing | Name, photo, role; sharing state; freshness status; battery; **no positions** and no place alerts |
| Anyone in the sender's groups, during SOS | The SOS and the sender's live position, regardless of the viewer's own state |
| Subscribed to a place (and sharing) | Arrival and departure of sharing members at that place, with the estimated time |
| Not in a shared group | Nothing |

What members **never** see about each other: e-mail address, Google identifier, phone model, IP address, history (until the person shares it in v1.1), access log, other groups a person belongs to.

## 5. Operator access

| Build and mode | Operator can see |
|---|---|
| Development/beta, operator mode on | Everything in §2, including positions and history, of users on the internal flavor. Every access is logged and visible to the person in the access log; explicit actions (fresh-location request, remote turn-on, history view) also show a notification on the phone, while the passive live overview is logged once per person per day without a notification (BR-04) |
| Any build, operator mode off (store release) | Account and group records without location data, device statuses, service statistics without coordinates |

The operator, like any administrator of a server, can technically read the database. This is stated in the privacy policy. Controls: access to the host by key over a private network, least-privilege roles, recorded manual operations.

## 6. The person's controls

| Right | How |
|---|---|
| Start, pause, stop sharing | One tap on the main screen or in the persistent notification |
| See who sees them | "Who sees what about me" screen and access log |
| Leave a group (withdraw consent for that group) | Group screen; immediate |
| Delete the account and data | In the app (MVP); on the web (store release). Location data is deleted within minutes, remaining records within 30 days, backups age out within the backup retention period |
| Delete history, change retention, export data | v1.1 |
| Revoke permissions | Android settings; the app reports the limited state honestly |

## 7. Children

The product is for people aged 13 and older; the person confirms it when creating the account. There are no child accounts and no flow in which one person sets up tracking of another. The product is not directed at children and is not submitted to the store's family programme.

## 8. Mapping to the Google Play Data safety form

Data types and terms follow the [Data safety help page](https://support.google.com/googleplay/android-developer/answer/10787469). The form is filled in for the store release and whenever a closed or open test track is used; apps only on the internal test track are exempt.

| Form data type | Collected | Shared | Purposes | Optional? | Notes |
|---|---|---|---|---|---|
| Location → Precise location | Yes | No | App functionality | Optional (the person may choose approximate or not share) | Sent to group members by the person's own action — a user-initiated transfer, not "sharing" in the form's sense |
| Location → Approximate location | Yes | No | App functionality | Optional | When the person grants only approximate access. Tile requests: see the open item below |
| Personal info → Name | Yes | No | App functionality, Account management | Required | From the Google profile |
| Personal info → Email address | Yes | No | Account management | Required | |
| Personal info → User IDs | Yes | No | App functionality, Account management | Required | Google account identifier, internal user id |
| Personal info → Other info | Yes | No | App functionality | Required | Profile photo URL — classification to confirm when filling in the form |
| App info and performance → Crash logs | Yes | No | Analytics (diagnostics) | Required | Through the error-tracking SDK; scrubbed |
| App info and performance → Diagnostics | Yes | No | App functionality, Analytics (diagnostics) | Required | Device status, battery level, self-check summary |
| Device or other IDs | Yes | No | App functionality | Required | Push token, random installation id |
| App activity → Other actions | To confirm | No | App functionality | Required | Fresh-location requests and place subscriptions recorded for transparency; conservative choice is to declare |
| Photos and videos, Contacts, Messages, Financial info, Health and fitness, Audio, Files and docs, Calendar, Web browsing | No | No | — | — | Not accessed |

Security practices to declare: data is encrypted in transit — yes; users can request deletion — yes (in-app and web).

Open items before the form is submitted:

1. **Map tile requests** made by the map library go to a server that is not ours. Decide whether they must be declared as approximate location collected or shared, based on the provider's published practice, or remove the question by self-hosting tiles. *Not verified.*
2. Re-read the SDK providers' Data safety guidance (Firebase Messaging, the error-tracking SDK, Google Play services location) and adjust the rows above. *Not verified.*
3. The operator capabilities do not exist in the store build, so they are not part of the form; the beta is distributed outside Google Play.

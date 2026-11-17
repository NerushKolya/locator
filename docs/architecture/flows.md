# Key flows

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [overview](overview.md) · [Android](android.md) · [backend](backend.md) · [data model](data-model.md) · [security](security.md) · [API guide](../api/README.md) · [realtime protocol](../api/realtime-protocol.md)

Sequence diagrams for the paths that carry the product's promises. Endpoint names match `api/openapi.yaml`. Participants: **App** (UI), **Engine** (location engine inside the app), **core**, **worker** (`core-worker`), **delivery**, **DB**, **FCM**, **Google**.

| # | Flow | Stories |
|---|---|---|
| 1 | Onboarding and permissions | US-007, US-020, US-090 |
| 2 | Sign-in and session | US-001, US-003, US-004 |
| 3 | Invitation | US-011, US-012 |
| 4 | Device report (point upload) | US-024, US-026 |
| 5 | Offline buffer and resend | US-026 |
| 6 | Live map: snapshot, stream, active view | US-028 AC1, US-030 |
| 7 | Fresh-location request | US-028 AC2–AC4 |
| 8 | Pause and resume | US-021, US-022, US-023 |
| 9 | Place transition and alert | US-042 |
| 10 | SOS | US-050–US-052 |
| 11 | Leaving or removal: access revocation | US-015, US-016 |
| 12 | Silence: watchdog, revival, no-updates alert | US-024 AC4, US-061 |
| 13 | Account deletion | US-005 |
| 14 | Operator action (development/beta) | US-091–US-093 |

---

## 1. Onboarding and permissions

```mermaid
sequenceDiagram
  actor U as User
  participant A as App
  participant OS as Android
  participant C as core
  U->>A: first launch
  A->>A: Google Play services available? (else "not supported")
  A->>U: age 13+ and terms confirmation
  Note over A,C: sign-in (flow 2)
  opt internal flavor
    A->>U: operator disclosure, must confirm
  end
  A->>U: why notifications are needed
  A->>OS: request POST_NOTIFICATIONS (Android 13+)
  A->>U: prominent disclosure (full screen, background wording)
  U->>A: Continue
  A->>OS: request FINE + COARSE location
  OS-->>A: precise / approximate / denied
  A->>U: why "Allow all the time" is needed
  U->>A: Open settings
  A->>OS: app location settings (system dialog on Android 10)
  OS-->>A: background granted / not granted
  A->>U: recommended: battery optimization exemption, phone-maker steps
  A->>U: result: what works, what is limited
  A->>C: device status (permission level, precision) in the first report
```

Rules: the system dialog appears only after the user's explicit action on the disclosure screen; leaving the screen is not consent; no location is read before step "request FINE + COARSE". Each step may be skipped; the result screen and the self-check name the consequences.

## 2. Sign-in and session

```mermaid
sequenceDiagram
  participant A as App
  participant G as Google (Credential Manager)
  participant C as core
  participant DB as DB
  A->>C: POST /v1/auth/nonce
  C-->>A: nonce (signed, 5 min)
  A->>G: Sign in with Google (server client id, nonce)
  G-->>A: ID token
  A->>C: POST /v1/auth/google {idToken, device, confirmations}
  C->>C: verify signature, issuer, audience, expiry, nonce
  alt new person without confirmations
    C-->>A: 409 confirmation_required (no account created)
  else
    C->>DB: upsert user · revoke previous device and its sessions (BR-10) · create device + session
    C->>DB: outbox push "session-revoked" to the old device (if any)
    C-->>A: accessToken (15 min) + refreshToken + user
  end
  A->>A: store tokens encrypted (Keystore)
  Note over A,C: later
  A->>C: POST /v1/auth/refresh {refreshToken}
  C->>DB: rotate token (reuse of an old token revokes the session)
  C-->>A: new token pair
```

The old phone learns about the replacement from the push or from the next `401 session_revoked`; it stops sharing, clears local data and shows "Signed in on another device" (US-004 AC2).

## 3. Invitation

```mermaid
sequenceDiagram
  actor O as Owner or admin
  actor I as Invitee
  participant A1 as App (inviter)
  participant A2 as App (invitee)
  participant C as core
  participant D as delivery
  O->>A1: Invite
  A1->>C: POST /v1/groups/{id}/invitations
  C-->>A1: code + link https://<domain>/i/<code> (72 h, single use)
  O-->>I: shares the link through any app
  alt app installed (verified app link)
    I->>A2: opens the link
  else not installed
    I->>C: GET /i/<code> (web page: how to install, the code)
    I->>A2: installs, signs in, enters the code
  end
  A2->>C: GET /v1/invitations/{code}
  C-->>A2: group name, inviter, members, consent text version
  I->>A2: Accept (explicit consent to share with this group)
  A2->>C: POST /v1/invitations/{code}/accept
  C->>C: checks: pending, not expired, limits (BR-12)
  C->>C: membership + consent record + invitation accepted (one transaction)
  C-->>A2: group
  C->>D: outbox: stream "member" (joined), push "group-event" to members
  A2->>I: offer to turn sharing on if it is off
```

Attempts with wrong codes are rate-limited per account and per IP address. An expired, used or revoked code returns `410 invitation_unavailable` and changes nothing.

## 4. Device report (point upload)

```mermaid
sequenceDiagram
  participant E as Engine
  participant C as core
  participant DB as DB
  participant W as worker
  participant D as delivery
  E->>E: new point → filter → PointBuffer
  E->>C: POST /v1/me/reports {points[], status, sentAgeMs…} (gzip)
  C->>C: authenticate · validate · receivedAt = now
  loop each point
    C->>C: fixedAt = receivedAt − ageMs
    C->>DB: insert location_points ON CONFLICT (device_id, point_id) DO NOTHING
  end
  C->>DB: upsert current_positions only if newer (BR-09)
  C->>DB: upsert device_statuses (lastSeenAt, nextReportInS, flags)
  C->>DB: outbox: "position" (class position) if the marker moved,<br/>"member" (class status) if status or freshness inputs changed
  C->>DB: enqueue PointsAccepted · read pending commands
  C-->>E: accepted[], rejected[], commands[], configVersion, serverTime
  E->>E: delete accepted points · apply commands
  DB-->>D: NOTIFY
  D-->>D: fan out to streams by entitlement
  W->>DB: PointsAccepted → place evaluation (flow 9), low-battery check
```

Notes:

- The whole request is one transaction; a retry of the same batch returns the same `accepted` list and stores nothing twice.
- A report with no points is a heartbeat: only the status is updated.
- When sharing is paused or not started the server accepts no points recorded **after** the pause began (`rejected: not_sharing`), except during an active SOS (BR-07). Points recorded while sharing was still on (`fixedAt < paused_at`, for example buffered offline before the pause) are accepted into history; they never move the live marker of a paused person.
- The response is the piggyback channel for commands; it works without push.

## 5. Offline buffer and resend

```mermaid
sequenceDiagram
  participant E as Engine
  participant C as core
  Note over E: no network: points accumulate in PointBuffer (≤ 7 days / 10 000)
  E->>E: network is back (connectivity callback)
  E->>C: report with the newest point (the map needs it first)
  C-->>E: accepted
  loop until the buffer is empty
    E->>C: report with up to 200 older points, oldest first
    C-->>E: accepted (late points go to history only)
  end
  Note over E,C: lost response → the same batch is sent again → the unique point id makes it harmless
```

Back-off: 5 s, 10 s, 20 s … up to 5 min with jitter; reset when connectivity returns. `4xx` other than `401`/`429` quarantines the batch and records it in the engine journal so one bad batch does not block the queue. Points older than the retention maximum are rejected as `too_old` and dropped by the phone.

## 6. Live map: snapshot, stream, active view

```mermaid
sequenceDiagram
  actor V as Viewer
  participant A as App (viewer)
  participant C as core
  participant D as delivery
  participant F as FCM
  participant T as Engine (member M)
  V->>A: opens the group map
  A->>C: GET /v1/groups/{id}/snapshot
  C->>C: visibility policy per member (BR-01, BR-02)
  C-->>A: members: status, freshness, battery, position or reason hidden
  A->>C: POST /v1/stream/tickets {groupId}
  C-->>A: ticket (60 s) with entitlements {group, positions: yes/no}
  A->>D: GET /v1/stream (Authorization: Bearer ticket)
  D->>D: verify signature and expiry · register connection
  D->>C: internal: presence {group, viewers}
  C->>C: viewer shares → active-view leases for sharing members (cap, cool-down)
  par piggyback
    C-->>T: command activeView in the next report response
  and push
    C->>D: outbox push "locate" (normal priority, collapse key, TTL 120 s)
    D->>F: send
    F->>T: message
  end
  T->>T: mode active_view · notification "<V> is viewing the map"
  T->>C: reports every ~10 s
  C->>D: outbox "position"
  D-->>A: event: position
  A->>A: move marker (only if newer)
  loop every 30 s while the map is open
    D->>C: presence heartbeat → leases renewed
  end
  V->>A: leaves the map
  A-xD: stream closed → presence removed → leases expire in ≤ 2 min
```

If the stream cannot be opened or breaks, the app polls the snapshot every 30 s and keeps trying to reconnect. A viewer who is not sharing gets a ticket without the position entitlement and a snapshot without positions; no leases are created for them (reciprocity).

Active view uses **normal** priority on purpose: it only changes the text of an already visible notification, and FCM may downgrade an app's high-priority messages when they often end without a user-visible notification ([FCM message priority](https://firebase.google.com/docs/cloud-messaging/android/message-priority)). High priority is kept for the explicit request (flow 7), SOS, place alerts, revival (flow 12) and the end of a pause (flow 8). A stationary member whose phone sleeps may therefore join the active view only at the next heartbeat; the map shows the honest position age meanwhile. The spike measures this choice (NFR-LOC-10).

## 7. Fresh-location request

```mermaid
sequenceDiagram
  actor V as Viewer
  participant A as App (viewer)
  participant C as core
  participant D as delivery
  participant F as FCM
  participant T as Phone of member M
  V->>A: "Refresh" on M's card
  A->>C: POST /v1/groups/{g}/members/{m}/location-requests
  C->>C: policy: shared group, both share, M not paused · rate limits (BR-12)
  C->>C: location_requests row · access_log entry for M · pending command
  C-->>A: 202 {requestId, known position with its age}
  C->>D: outbox push "locate" {requestId} to M (high priority)
  D->>F: send
  F->>T: message
  alt service running
    T->>T: active_view for 2 min · notification "<V> requested your location"
  else service not running
    T->>T: priority still high? background permission? location on?
    T->>T: start foreground service + notification "<V> requested your location"
  end
  T->>C: report with a fresh point (flag on_demand, requestId)
  C->>C: request fulfilled
  C->>D: outbox "position" + "request" (fulfilled)
  D-->>A: events
  Note over A,C: no fresh point in 60 s → worker marks timed_out →<br/>event "request" (timed_out) → "Could not get a fresh location"
```

Rejections are immediate and explicit: `409 target_paused`, `409 viewer_not_sharing`, `429 rate_limited`. If the phone cannot start the service (priority downgraded, permission missing) it shows "Open the app to share your location" and reports the reason in its next status.

## 8. Pause and resume

```mermaid
sequenceDiagram
  actor U as User
  participant A as App / Engine
  participant C as core
  participant D as delivery
  U->>A: Pause (1 h / until a time / until I resume) — app or notification
  A->>A: stop collection now · rev + 1 · service stops ·<br/>quiet notification "Paused until … · Resume" · schedule end-of-pause alarm
  A->>C: PATCH /v1/me/sharing {state: paused, pauseUntil, rev}
  C->>C: apply if rev is higher · cancel leases and pending requests for U
  C->>D: outbox: "member" (paused since/until) to U's groups,<br/>control "access-changed" for U
  D-->>D: close U's streams with "reconnect" (U loses position entitlement)
  Note over A,C: PATCH failed → retried; the next report carries the same state
  Note over C: worker at pause_until
  C->>D: outbox push "pause-ended" to U (high priority)
  D->>A: via FCM
  alt push or alarm arrives and Android allows the start
    A->>A: start service (first of: high-priority push, inexact alarm)
    A->>C: PATCH {state: active, rev} · first report
  else start not allowed (priority downgraded, no exemption)
    A->>U: notification "Pause ended — tap to resume"
    C->>C: worker: pause_until passed → "Pause ended, waiting for an update"
  end
```

Two signals end a timed pause. The server's high-priority `pause-ended` push is an allowed background-start exemption ([background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)); the action was scheduled by the person, and the result is visible (the persistent notification returns). The phone's own inexact alarm is a fallback without the network, but an inexact alarm is not an exemption: it can restart the service only when the person exempted the app from battery optimization. Exact alarms need a special permission that the app does not request. If neither path is allowed, the notification path is used. The spike measures each path on the test phones (D-27); the pause specification makes the final choice.

While paused: others see "Paused since/until …" and the dimmed last position; U sees names and statuses but no positions, gets no place alerts and cannot request fresh locations; SOS still works in both directions.

## 9. Place transition and alert

```mermaid
sequenceDiagram
  participant W as worker
  participant DB as DB
  participant D as delivery
  participant F as FCM
  participant S as Subscriber's phone
  Note over W: PointsAccepted for member M (from flow 4)
  W->>DB: places of M's groups · place_presence rows (locked)
  loop each place, points in time order
    W->>W: ignore inaccurate, outlier, mock and late points
    W->>W: update candidate state · confirm after 2 points or 60 s
  end
  opt state confirmed and changed (not from "unknown")
    W->>DB: place_transitions row · PlaceTransitionDetected
    W->>W: recipients = subscribers of the place who share, did not mute M,<br/>and M shares (visibility policy)
    W->>DB: outbox push "place" {placeId, memberId, kind, occurredAt} per recipient
    W->>DB: outbox stream "place" for open maps
    D->>F: send (high priority: user-visible alert)
    F->>S: message
    S->>S: notification "<name> arrived at <place> · HH:MM" (text built on the phone)
  end
```

Places are evaluated only on the server, from points the server already receives ([ADR-013](adr/ADR-013-places-evaluated-on-server.md)). Consequences: the delay equals the upload interval; nothing is detected while the member is paused or offline; an edit of a place takes effect immediately and resets its presence rows to `unknown`.

## 10. SOS

```mermaid
sequenceDiagram
  actor U as Sender
  participant A as App / Engine
  participant C as core
  participant D as delivery
  participant F as FCM
  participant R as Recipients' phones
  U->>A: press and hold SOS (~2 s)
  A->>A: sosId = new id · mode sos · remember previous sharing state
  A->>C: PUT /v1/me/sos/{sosId} {last known point, startedAgeMs}
  Note over A: no network → queued, UI shows "Not delivered yet", retried
  C->>C: sos_events row (idempotent by id)
  C->>C: recipients = all members of all groups of U (BR-07)
  C->>D: outbox: push "sos" (high priority) + stream "sos" (class sos)
  D->>F: send
  F->>R: message
  R->>R: high-importance notification "<name> sent SOS"
  loop up to 15 min
    A->>C: reports every ~5 s (accepted even if U was paused)
    C->>D: outbox "position" (class sos)
    D-->>R: live position for open maps
  end
  alt "I am safe" / cancel
    A->>C: POST /v1/me/sos/{sosId}/end
  else 15 min
    C->>C: worker ends the SOS if the phone did not
  end
  C->>D: outbox: push + stream "sos" (ended)
  A->>A: restore previous mode and sharing state (including pause)
```

During an SOS the sender's positions carry class `sos`, which every member's ticket includes regardless of reciprocity. Recipients open the SOS with `GET /v1/sos/active`. The SOS screen always offers "Call 112", which opens the dialer and needs no permission.

## 11. Leaving or removal: access revocation

```mermaid
sequenceDiagram
  actor O as Owner or admin
  participant A as App (O)
  participant C as core
  participant D as delivery
  participant X as App (removed member)
  O->>A: remove member X
  A->>C: DELETE /v1/groups/{g}/members/{x}
  C->>C: role check · delete membership · close consent ·<br/>delete X's subscriptions and presence rows in g
  C->>D: outbox: control "access-changed" {X} · stream "member" (removed) ·<br/>push "group-event" to X and members
  D->>X: event: reconnect, then close X's streams
  X->>C: POST /v1/stream/tickets {g}
  C-->>X: 404 (not a member)
  Note over C: from the commit on, every REST call of X about g returns 404
```

The REST API is safe at commit time because every call checks membership. The stream is safe within seconds through the control event, and in the worst case within the maximum stream lifetime; tickets are valid for 60 s only. Leaving a group is the same flow started by the member; an owner must transfer ownership or delete the group first.

## 12. Silence: watchdog, revival, no-updates alert

```mermaid
sequenceDiagram
  participant W as worker (every minute)
  participant DB as DB
  participant D as delivery
  participant F as FCM
  participant T as Silent phone
  participant M as Members
  W->>DB: devices with sharing active and lastSeenAt older than 2 × nextReportInS
  opt first time in this episode
    W->>DB: outbox push "locate" {reason: revival} · access_log "revival_push_sent"
    D->>F: send (high priority)
    F-->>T: may or may not arrive
    T->>T: restart service if allowed + notification "Sharing was restored",<br/>otherwise "Sharing stopped — tap to resume"
  end
  W->>DB: devices silent longer than 4 × nextReportInS
  opt not yet offline in this episode
    W->>DB: freshness_offline_since = now · alert_episodes (no_updates)
    W->>DB: outbox: stream "member" (offline) · push "no-updates" to members who keep the alert on
    D->>F: send (normal priority)
    F->>M: quiet notification "No updates from <name> since HH:MM"
  end
  Note over T,W: next report from T → episode closed silently, map updates
```

The server does not guess the reason for silence; the status text is neutral. Only one revival push and one alert are sent per episode.

## 13. Account deletion

```mermaid
sequenceDiagram
  actor U as User
  participant A as App
  participant C as core
  participant W as worker
  participant D as delivery
  U->>A: Delete account → confirm
  A->>A: stop sharing
  A->>C: DELETE /v1/me
  C->>C: revoke all sessions · status deletion_pending ·<br/>for each group: pass ownership or delete the group · remove memberships
  C->>D: outbox: control "access-changed" · stream "member" (left) · push "group-event"
  C->>W: enqueue AccountDeletionRequested
  C-->>A: 204
  A->>A: clear tokens, buffer, cache → sign-in screen
  W->>W: delete current position, all points, presence,<br/>transitions, SOS events, requests, leases, commands, push token
  Note over W: daily purge after 30 days: users row, devices,<br/>consent records, access log of the user
```

Deletion steps are idempotent and retried until done. Signing in again during the 30-day window creates a fresh account; nothing is restored. The web deletion page (store release) authenticates with Google and calls the same use case.

## 14. Operator action (development and beta builds)

```mermaid
sequenceDiagram
  actor Op as Operator
  participant B as backoffice
  participant C as core (admin API)
  participant D as delivery
  participant T as Phone of user X (internal flavor)
  Op->>B: request X's location / turn X's sharing on
  B->>C: POST /admin/v1/... (service token with operator id)
  C->>C: OPERATOR_MODE on? X's device is internal flavor? → else 403
  C->>C: access_log entry for X · pending command
  C->>D: outbox push "locate" {reason: operator} / "start-sharing"
  D->>T: via FCM
  T->>T: notification "The operator requested your location" /<br/>"Sharing was turned on by the operator" + persistent notification
  T->>C: PATCH /v1/me/sharing (changedBy operator) · reports
  C-->>B: result
```

The phone applies the command itself and always shows it. If Android does not allow a background start, the user gets a notification to resume and nothing starts silently (US-093 AC2). In the `store` flavor the handler for `start-sharing` does not exist, and the core refuses the action for such devices.

The operator's passive live overview (the map of all users) sends nothing to phones: the core writes one access-log entry `operator_viewed_live` per person per day while the operator's stream is open (BR-04, US-091 AC2). Until the back office exists, the same operator actions are console commands of the core and follow the same rules.

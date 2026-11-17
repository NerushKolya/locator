# ADR-014: Push as a wake channel and alert transport behind an interface

- Status: Proposed
- Date: 2026-11-15; revised 2026-11-17 in the blueprint review (end-of-pause message, priority rule for active view)

## Context

The server cannot query a phone; it can only send a push and hope. High-priority FCM messages wake a sleeping device and permit a background start of a foreground service, but they are meant for user-visible content: if messages do not lead to notifications, FCM may lower their priority ([message priority](https://firebase.google.com/docs/cloud-messaging/android/message-priority)). The current high-priority model is being replaced by a new API with separate message kinds for visible notifications and non-urgent sync, still in preview ([Doze and standby](https://developer.android.com/training/monitoring-device-state/doze-standby), [FCM v2 API](https://firebase.google.com/docs/cloud-messaging/send/v2-api)). FCM is a third party, and product rule BR-13 forbids coordinates outside the product.

## Options

1. **Push as the foundation:** wake the phone silently for every point. Fragile, contrary to FCM's intent, no future.
2. **Persistent connection from the phone.** Independent of FCM. Keeps the radio awake; battery cost.
3. **Running service + piggyback commands as the base, push as accelerator and reviver.**

## Decision

Option 3.

- The base channel for commands is the response to the phone's own device report (piggyback). It needs no push.
- Push is used to (a) switch a running service to active view quickly, (b) deliver a fresh-location request, (c) revive a stopped service once per silence episode, (d) resume sharing at the end of a timed pause, and (e) deliver alerts.
- On the phone the channel sits behind a `WakeChannel` interface; the first implementation is FCM high-priority data messages. Before starting a service from a push, the app checks the delivered priority, the background permission and location availability; otherwise it shows a notification instead of failing.
- Every request that reaches the phone by push is visible: a notification, or the persistent notification's text for active view.
- Pushes are sent by `delivery` with the official Firebase Admin SDK and carry a type and identifiers only. Texts are built on the phone from string resources.
- Priority: high only for messages that end in something the person sees — SOS, fresh-location requests, place alerts, revival and the end of a timed pause (the restarted service shows its notification); **normal for active view** and everything else. Active view only changes the text of an already visible notification, and spending high priority on it could make FCM downgrade the messages that need it. A stationary phone may join an active view at its next heartbeat; the map shows the honest position age meanwhile.
- The app records the requested and the delivered priority of every message (NFR-LOC-10).

## Consequences

- The main function does not depend on push delivery; push only shortens delays.
- The design matches where FCM is heading: wake-ups are tied to something the user sees.
- A high-priority message is an allowed exemption for starting a foreground service from the background; an inexact alarm or a WorkManager job is not ([background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)). Revival and the end of a pause therefore depend on push, and the spike measures every start path on the test phones (D-27).
- A hibernated app (unused for months) receives no push at all, including high priority ([app hibernation](https://developer.android.com/topic/performance/app-hibernation)); onboarding and the self-check address this, not the wake channel.
- Whether the share of downgraded messages stays low in real use is measured in the spike and the beta (NFR-LOC-10).
- Migration to the new FCM API after its general availability affects one implementation of `WakeChannel` and the message format (localized notifications rendered by the system would need localization keys and arguments in the payload).
- Devices without Google Play services could get another implementation later.

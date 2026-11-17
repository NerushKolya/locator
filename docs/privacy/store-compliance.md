# Store compliance

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [data inventory](data-inventory.md) · [permissions rationale](permissions-rationale.md) · [feature matrix](../product/feature-matrix.md) (Play release) · [security](../architecture/security.md) · [ADR-016](../architecture/adr/ADR-016-operator-capabilities-flavor-and-switch.md)

Checklist for publishing on Google Play. The family beta is distributed outside the store, but the app is built to these rules from the first version (product principle P7). Policy facts were read on the linked official pages on 2026-11-15; policies change, so every linked page is read again in full before submission. Items marked *Not verified* could not be confirmed from an official page.

## 1. What is required and where it is handled

| # | Requirement | Source | How the product meets it | Release |
|---|---|---|---|---|
| C1 | Background location only for a core feature with clear user benefit; declaration with one feature and a short video | [Location permissions policy](https://support.google.com/googleplay/android-developer/answer/9799150) | Live sharing with family groups is the core feature; texts in [permissions rationale §6.1](permissions-rationale.md) | Play |
| C2 | Prominent disclosure before the permission dialog: in-app, in the normal flow, names "location" and background use, lists features, needs an affirmative action | Same; [best practices](https://support.google.com/googleplay/android-developer/answer/11150561); [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | Onboarding screen (US-020); texts in [§4.2](permissions-rationale.md) | MVP |
| C3 | Foreground service types declared in the manifest and in Play Console, with description, impact and video | [Foreground service declaration](https://support.google.com/googleplay/android-developer/answer/13392821) | Type `location`, use case "user-initiated location sharing"; [§6.2](permissions-rationale.md) | MVP (manifest), Play (console) |
| C4 | Data safety form is complete and accurate, including data sent by SDKs | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) | [Data inventory §8](data-inventory.md) | Play (and any closed or open test track) |
| C5 | Privacy policy at a working public URL, linked in the store listing and in the app | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | `/privacy` on our domain; reachable in two taps from settings (US-073) | MVP |
| C6 | Account deletion: an in-app path and a web link; associated data deleted; retained data disclosed | [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111) | In-app deletion (US-005, MVP); web page `/account/delete` (US-006); retention disclosed in the policy | MVP / Play |
| C7 | The app is not stalkerware: no tracking of another adult, no hidden operation | [Malware policy, Stalkerware](https://support.google.com/googleplay/android-developer/answer/9888380) | §3 below | Play |
| C8 | Target API level current | [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878) | Target API 36; raised when the requirement changes | MVP |
| C9 | New personal developer accounts: closed test with at least 12 testers opted in for 14 consecutive days before production access | [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) | §5 below | Play |
| C10 | Reviewer can access all features | [Location permissions policy](https://support.google.com/googleplay/android-developer/answer/9799150) (listed rejection reason) | §6 below | Play |
| C11 | Request to ignore battery optimization only for acceptable use cases | [Doze and standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | Family safety app; fallback is a link to the settings list ([§6.3](permissions-rationale.md)) | MVP |
| C12 | Families policy applies only if children are a target audience | [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335) | Audience 13+; no child-directed design or wording; target audience declared accordingly | Play |
| C13 | No deceptive or undisclosed data use; no ads SDKs to declare | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) | No advertising, analytics or data sales (principle P6) | MVP |

## 2. Data safety form

Source table: [data inventory §8](data-inventory.md). Process:

1. Regenerate the table from the data inventory at the time of submission.
2. Compare with each SDK provider's Data safety guidance (Firebase Cloud Messaging, Google Play services location, the error-tracking SDK, the map library). Data transmitted by libraries counts as the app's collection.
3. Resolve the open item on map tile requests (declare, or self-host tiles).
4. Answer the security questions: encrypted in transit — yes; deletion request mechanism — yes.
5. Keep a dated copy of the submitted answers in `docs/privacy/` so later changes are reviewable.

The form is not needed while the app is only on the internal test track; it is needed for closed testing, which is a mandatory step (C9).

## 3. Stalkerware checklist

Google Play does not allow apps that track another person even with their knowledge and permission, except parental and enterprise monitoring tools marked with `isMonitoringTool`. Locator must stay clearly on the other side: each adult shares **their own** location by their own action.

| # | Property of the store build | Verified by |
|---|---|---|
| K1 | Sharing can be started only by the person on their own phone | No server command starts sharing in the `store` flavor; build check for absent operator classes |
| K2 | No remote turn-on, no "sees everyone" role | Operator module not compiled; server refuses operator actions for `store` devices; operator mode off in the store environment |
| K3 | The persistent notification is shown whenever location is collected and is never hidden or disguised | Service design; device test matrix |
| K4 | The app icon is never hidden; no disguised name | Manifest review |
| K5 | Every fresh-location request is visible on the target phone and logged | US-028, US-071; automated test |
| K6 | The person sees who can see them and who is alerted about them | "Who sees what about me" (US-070) |
| K7 | Stop or pause in one tap from the main screen and the notification | US-021, US-022 |
| K8 | No invisible watchers | Reciprocity (US-023) with server tests |
| K9 | Store listing and screenshots do not present the app as a tool for monitoring someone else | Listing review |
| K10 | No accessibility service, no overlay permission, no device-admin role, `isMonitoringTool` not set | Manifest review |
| K11 | "Only on request" mode: the person chooses it; each request produces a visible notification | US-100 |

The final scope of operator rights for the public release is an open product decision that must be closed before submission.

## 4. Declarations and assets to prepare

| Item | Content | Source text |
|---|---|---|
| Background location declaration | One core feature, why foreground is not enough, video link | [Permissions rationale §6.1](permissions-rationale.md) |
| Foreground service declaration | Type `location`, use case, impact, video link | [§6.2](permissions-rationale.md) |
| Demo video (30 s or less) | Disclosure → permission dialogs → user turns sharing on → notification → background → update on another phone | Recorded on a real phone |
| Privacy policy | Built from the data inventory: data, purposes, recipients, retention, rights, operator access, contact | Written for the family beta; reviewed for the store |
| Terms of use | Age 13+, acceptable use (no covert tracking of others), no guarantee of continuous tracking, SOS does not replace emergency services | Written for the family beta |
| Account deletion URL | `/account/delete`, names the app, explains what is deleted and what is retained and for how long | Store release |
| Content rating questionnaire, target audience (13+), ads declaration (no ads) | Console forms | Store release |
| Store listing | Name, descriptions in Ukrainian and English, screenshots, icon | Design stage |
| App access instructions for review | §6 | Store release |

## 5. Closed testing

For a personal developer account created after 13 November 2023:

- Registration costs a one-time fee and requires identity verification and a verified Android device ([registration](https://support.google.com/googleplay/android-developer/answer/6112435)).
- A closed test must run with **at least 12 testers opted in for 14 consecutive days** before applying for production access; the review of the application usually takes up to a week ([testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)).
- The Data safety form, the privacy policy and the declarations are needed already for the closed track.

Plan: the family beta runs outside the store first (operator capabilities on, disclosed). For the closed test the **store flavor** is used, with operator capabilities absent, so the test also validates the build that will be published. Twelve people from family and friends with Android phones are needed; recruiting them is a task for the owner.

## 6. Access for the reviewer

Sign-in is with Google only and the main screen needs a group with another sharing member, so a reviewer cannot evaluate the app alone. Before submission:

- Provide a dedicated Google test account in Play Console "App access", with step-by-step instructions.
- Prepare a demo group for that account with a second test member whose position is updated by a test phone or a scripted client, so the map, place alerts and requests can be seen.
- Make sure the demo data contains no real person's location.

This is an open task for the store release; the scripted demo member must be designed so it cannot be used to fake real members.

## 7. Upcoming policy changes to track

| Change | Status | Action |
|---|---|---|
| For apps targeting API 37 from 27 January 2027: one-time and infrequent location access through the system location button; precise location requires a declaration | Announced as upcoming on the [sensitive permissions page](https://support.google.com/googleplay/android-developer/answer/16558241); effect on continuous sharing is not stated | Stay on target API 36 until required; prepare the justification "continuous precise sharing with family is the core feature" |
| FCM: the high-priority message model is being replaced by a new API with separate visible-notification and background-sync messages | Preview ([FCM v2 API](https://firebase.google.com/docs/cloud-messaging/send/v2-api)) | Push sits behind `WakeChannel`; revisit when generally available |
| Yearly target API increase | Recurring | Calendar reminder; update and re-test background behaviour on each new Android version |

## 8. Pre-submission checklist

- [ ] Store flavor built from a tagged commit; build check confirms no operator classes; operator mode off on the store server.
- [ ] Device test matrix passed on the store flavor.
- [ ] Prominent disclosure texts reviewed against the current policy pages, in Ukrainian and English.
- [ ] "Only on request" mode available (US-100).
- [ ] Web account deletion works and is linked in the listing and in the policy.
- [ ] Privacy policy matches the data inventory and names retention periods, recipients and operator access.
- [ ] Data safety form filled in from the data inventory; SDK guidance re-read; tile-request item resolved.
- [ ] Background location and foreground service declarations submitted with the video.
- [ ] Reviewer account and demo group prepared.
- [ ] Closed test: 12 testers opted in for 14 consecutive days.
- [ ] Stalkerware checklist (§3) walked through and signed off by the owner.
- [ ] All linked policy pages re-read in full on the day of submission.

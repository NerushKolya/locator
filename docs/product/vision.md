# Product vision

> Status: draft for blueprint review · Last updated: 2026-11-15
> Working name: **Locator** (the brand name is chosen in the design stage).
> Related: [personas](personas.md) · [user journeys](user-journeys.md) · [PRD](prd.md) · [feature matrix](feature-matrix.md) · [glossary](glossary.md)

## 1. One-liner

**A family map on equal terms, honest to the people on it.**

Members of a small group (a family, close friends) see each other's location on a map, by explicit and mutual consent. Everyone controls their own sharing, everyone can see who sees them, and the map always says how fresh each position is.

## 2. The problem

Families want a quick answer to "is everyone OK, and where are they roughly right now?" without phone calls and without one person silently watching another.

Existing products leave three gaps:

1. **"It does not update."** The most common complaint across the category is a position that is hours old while the app looks normal. Android aggressively limits background apps, and phone makers add their own restrictions. Most apps show only "updated N minutes ago" and never explain *why* the data is old.
2. **Watching feels like surveillance.** In "parent watches child" products the watched person controls nothing and sees nothing. Teenagers react by switching phones off or uninstalling. One market leader responded with a feature that blurs the exact position ([TechCrunch, 2020](https://techcrunch.com/2020/10/12/family-tracking-app-life360-launches-bubbles-a-location-sharing-feature-inspired-by-teens-on-tiktok)).
3. **Trust in the vendor.** Location history is the most sensitive personal data a phone produces. A leading family-locator app sold near real-time location data of tens of millions of users to data brokers until 2022 ([The Markup, 2021](https://themarkup.org/privacy/2021/12/06/the-popular-family-safety-app-life360-is-selling-precise-location-data-on-its-tens-of-millions-of-user), [The Markup, 2022](https://themarkup.org/privacy/2022/01/27/life360-says-it-will-stop-selling-precise-location-data)). Other apps in the category carry third-party advertising.

## 3. Who it is for

- **Families and close friends, 13 years and older.** Every member has their own account and gives their own consent. No child accounts, no "parent adds child" flow.
- **Ukraine first** (Ukrainian interface by default, English as the second language), built so that more languages and countries can be added.
- **Android only** for now. iOS may come later as a separate client of the same API.
- First year: the author's family and friends (up to ~100 people). Architecture and documents are prepared for a public Google Play release.

Detailed personas: [personas.md](personas.md).

## 4. Product principles (non-negotiable)

These rules override any feature request. They are covered by tests and by the release checklist.

| # | Principle | What it means in the product |
|---|---|---|
| P1 | **Consent of the located person** | Location is collected and shared only after the person explicitly turns sharing on. Sharing can be paused or stopped in one tap from the main screen and from the persistent notification. |
| P2 | **Nothing hidden** | The app icon and the Android persistent notification are never hidden. Every fresh-location request is visible on the target phone. The app must not be usable as stalkerware. |
| P3 | **Group boundary** | A user can never get the location of someone they do not share a group with. Authorization is enforced per group on the server and covered by automated tests. |
| P4 | **Reciprocity** | Inside a group you see other members' locations only while you share your own location with that group. No invisible watchers. (Operator exception in development and beta builds — see §8.) |
| P5 | **Honest freshness** | The app never presents an old position as current. It shows how old the position is, when the phone last made contact, and the reason when it is known (paused, location off, no permission, power saving). |
| P6 | **No ads, no data sales, no third-party analytics** | Location data is used only to run the service for the group. Crash reports never contain coordinates or tokens. |
| P7 | **Store-policy compliance from day one** | Prominent disclosure before the background-location permission, correct foreground service type, accurate Data safety form — even for builds that are installed outside Google Play. |
| P8 | **Your data stays yours** | Account deletion is available in the app and on the web; history retention is limited and configurable; data export is provided. |

## 5. How we are different

Comparison is based on public product pages and help centres; we do not copy any competitor's interface, texts or code.

| Area | Typical in the category | Locator |
|---|---|---|
| Freshness | "Updated N min ago" only | Two clocks — **position age** and **last contact** — plus the reason for silence when the phone reports it |
| Reliability | Users find out by themselves that the app was killed | **Reliability self-check** on the phone: permissions, battery optimization, phone-maker restrictions, power saving, with direct links to the right settings |
| Transparency | Watched person does not know who watches | **"Who sees what about me"** screen: groups I share with, who gets arrival/departure alerts about me, who requested my fresh location, and any operator actions |
| Symmetry | Parent watches child | **Equal members**, reciprocity rule (P4), visible pause |
| Fresh location on demand | Hidden or paid | Visible on the target phone every time; Google applies the same rule in its own sharing feature ([Find Hub help](https://support.google.com/android/answer/15992026?hl=en)) |
| Connectivity gaps | Points lost when offline | Offline buffer on the phone; on reconnect the newest point first, then the rest in order; "last known position before losing connection" |
| Business model | Ads, data deals, paid tiers | Free, no ads, no data sales, no monetization planned |
| Language | Often no Ukrainian | Ukrainian by default, English second |

## 6. What we honestly promise — and what we do not

**We promise:**
- The map shows how old each position is and when the phone last made contact.
- When the phone tells us why it is silent (paused, location off, permission revoked, power saving), group members see that reason.
- Sharing frequency adapts: frequent while moving, rare while stationary, more frequent while someone looks at the map. The target is no more than ~5% battery per day on a typical day. This is a target, not a measured fact yet: it is checked on real phones in the location spike.
- Points recorded without network are kept on the phone and delivered later without duplicates.

**We do not promise:**
- A continuous track under all conditions. Android and phone makers may stop background work; we detect and report it rather than hide it.
- Any updates after the user force-stops the app. Android does not allow it.
- Accuracy indoors or in the metro.
- That SOS replaces emergency services. The SOS screen always offers a one-tap call to the emergency number (112 in Ukraine).

## 7. Scope by release

Full breakdown with complexity, risk and dependencies: [feature-matrix.md](feature-matrix.md).

| Release | Goal | Main content |
|---|---|---|
| **MVP — family beta** | The family uses it every day | Google sign-in, groups with roles and invitations, background sharing with visible pause, group map with freshness/accuracy/battery/status, fresh location while viewing, places with arrival/departure alerts, SOS, low-battery and "no updates" alerts, reliability self-check, "who sees what about me", leave/remove/delete account, Ukrainian + English |
| **v1.1 — history** | Answer "where was I / where were you" | Location history with retention (30 days by default, configurable), sharing history with a group or a person, history deletion, data export, per-group sharing switch |
| **Play — store release** | Pass Google Play review | "Share only on request" mode, operator capabilities removed from the store build, web account deletion, Data safety and background-location declaration, closed testing |
| **v2** | Grow beyond one family | Approximate location per group, email sign-in, trip detection, home-screen widget |
| **Later** | Ideas that the architecture must not block | Web panel for users, iOS, Wear OS, chat, devices without Google Play services, end-to-end encryption |

## 8. Operator capabilities in development and beta builds

During development and the family beta the project owner acts as the **operator** (super-admin) to debug location delivery. In development and beta builds only, the operator can see all users' positions and history, request a fresh location, and turn sharing on remotely.

Constraints that keep this honest:
- Every beta participant is told about these capabilities **before installing**, and the app shows them again during onboarding.
- Android always shows the persistent notification while location is being shared; we never hide it.
- Every operator action is recorded and visible to the affected person in "Who sees what about me". Explicit actions (fresh-location request, remote turn-on, history view) also show a notification on the phone; watching the live map of all users is recorded once a day without a notification.
- The capabilities sit behind a build flag. **The Google Play build does not contain them.** Google Play policy does not allow tracking other adults even with their permission ([Google Play: Stalkerware](https://support.google.com/googleplay/android-developer/answer/9888380)).
- The final scope of operator rights is decided before the Google Play release.

## 9. Success measures

| Measure | Target | How we check |
|---|---|---|
| Daily use | Every family member opens the map or relies on alerts at least several times a week during the beta | Beta feedback, server statistics without coordinates |
| Freshness while moving | Median position age ≤ 90 s, 95th percentile ≤ 5 min | Location spike and beta, real phones |
| Stationary heartbeat | At least every 15 min; 95% arrive within 20 min | Location spike |
| Battery | ≤ ~5% per typical day | Location spike measurements (target, not yet measured) |
| Group boundary | 0 cases of location visible outside a shared group | Automated authorization tests, security review |
| Trust | No beta participant says "I did not know who could see me" | Beta interview |
| Store readiness | Background-location declaration approved by Google Play | Play release |

Detailed non-functional requirements are defined in [nfr.md](../quality/nfr.md).

## 10. Constraints

- **Budget:** $0 for infrastructure until a real limit is hit. Free tiers and a free domain.
- **Platform:** Android 10 (API 29) and newer, Google Play services required. Devices without them get a clear "not supported" message.
- **Hosting and data:** servers in the EU.
- **Vendors:** no services, SDKs, map providers, stores, hosting or libraries from the Russian Federation.
- **Team:** one developer; no deadlines. Simplicity wins over features.

## 11. Non-goals

- Parental-control or employee-monitoring tool; any hidden or covert mode.
- Children under 13 and child accounts.
- Advertising, selling or sharing location data with third parties, third-party analytics.
- Monetization (not planned; the architecture does not prepare for it).
- iOS in the first releases.

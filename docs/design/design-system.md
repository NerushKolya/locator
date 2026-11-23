# Design system

> Status: draft for blueprint review · Last updated: 2026-11-23
> Related: [PRD](../product/prd.md) · [personas](../product/personas.md) · [user journeys](../product/user-journeys.md) · [permissions rationale](../privacy/permissions-rationale.md) · [store compliance](../privacy/store-compliance.md) · [Android architecture](../architecture/android.md) · [NFR](../quality/nfr.md)

How the app looks and speaks: brand, colour, type, components, the icon, the rules that every screen follows and the states that the mockups do not draw. It is the contract for the `core:designsystem` and `core:ui` modules and for the screen specs under `specs/`.

**Sources of truth.** The approved mockups in Figma ([file](https://www.figma.com/design/LJ4Ve24dps42OWWlUoQygW/Locator-%E2%80%94-M3-Kit), access by invitation; [prototype](https://www.figma.com/proto/LJ4Ve24dps42OWWlUoQygW/Locator-%E2%80%94-M3-Kit?page-id=60811%3A73&node-id=60818-15159&starting-point-node-id=60818-15159&scaling=scale-down-width)) show the look; this document records the rules behind them and the cases they do not show; exported frames are in [`screens/`](screens/). Texts on permissions, consent and onboarding come only from [permissions-rationale.md](../privacy/permissions-rationale.md). Where the mockups and this document differ, the differences are listed in [section 11](#11-deviations-and-open-points).

The mockups use Ukrainian copy (the primary UI language, US-110). This document gives English reference strings; the production strings live in Android string resources, Ukrainian and English.

## 1. Brand in the interface

| | Decision |
|---|---|
| Working name | **Loomi** (from "lumi", light; written in Latin letters in every app language). The `applicationId` is `io.github.nerushkolya.locator` (chosen in the walking skeleton, independent of the brand name). A Google Play search for the name is still to be done by the owner before release |
| Idea | "A light in the window": your people are in place and all is well. No eye, crosshair or radar symbols (no surveillance imagery) |
| Tone | Warm in words, exact in facts: calm and kind, but every status, age and reason is concrete ("until 18:00", "reason: power saving") |
| Address | Formal "you" in Ukrainian (Ukrainian second-person plural) |
| Never in system texts | Exclamation marks, "Attention!", emoji, words of control or surveillance ("track", "monitor", "supervise", "control", "kids"). Use "share location", "nearby", "family", "groups" (store compliance K9) |
| Red | Only for errors and SOS |

Reference texts (English renderings of the approved Ukrainian copy):

- Arrival: "**Maks is at school** · arrived 08:12"
- Empty group: "It is just you here so far. Invite your people — they will see you once they share their location too."
- Pause: "Maks: paused until 18:00. The location is not updated — that is by design."
- Silence: "No updates from Halyna for 2 h. Reason: power saving."

## 2. Colour

### 2.1 Palette

The scheme is generated from the seed **`#1B7F79`** ("sea wave", tonal spot) with material-color-utilities 0.3.0. It sits between the Material 3 Design Kit's *Teal* scheme (used in the mockups) and a pure seed scheme; the difference is minimal (primary `#006A65` against `#006B60`). Plan B, if the colour clashes with parks or water on the real map: seed `#8A3B6E` ("plum").

Why this colour: it is calm, contrasts with the beige OpenFreeMap "Liberty" basemap, and avoids the colours the map already uses for points of interest (blue, purple) and for roads and warnings (yellow, orange).

Roles used by the app (full list: [`theme/Color.kt`](theme/Color.kt)):

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` / `onPrimary` | `#006A65` / `#FFFFFF` | `#81D5CE` / `#003734` | Main buttons, live member marker (always the light value, see 2.3), links |
| `primaryContainer` / `onPrimaryContainer` | `#9DF2EA` / `#00504C` | `#00504C` / `#9DF2EA` | The "You are sharing" card, avatar of the account owner, success marks |
| `secondaryContainer` / `onSecondaryContainer` | `#CCE8E5` / `#324B49` | `#324B49` / `#CCE8E5` | Tonal buttons ("Fix"), selected chips |
| `tertiaryContainer` / `onTertiaryContainer` | `#CFE4FF` / `#304962` | `#304962` / `#CFE4FF` | Neutral hints, "needs a look" icon background (2.4) |
| `error` / `onError` | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | SOS banner, SOS marker, SOS button, real errors |
| `errorContainer` / `onErrorContainer` | `#FFDAD6` / `#93000A` | `#93000A` / `#FFDAD6` | Inline error text blocks |
| `surface`, `background` | `#F4FBF9` | `#0E1514` | Screens |
| `surfaceContainer*` (Lowest…Highest) | `#FFFFFF` … `#DDE4E2` | `#090F0F` … `#2F3635` | Sheets, cards, list areas, map chips |
| `onSurface` / `onSurfaceVariant` | `#161D1C` / `#3F4947` | `#DDE4E2` / `#BEC9C7` | Primary and secondary text |
| `outline` / `outlineVariant` | `#6F7978` / `#BEC9C7` | `#889391` / `#3F4947` | Borders, stale marker outline, dividers |
| `inverseSurface` / `inverseOnSurface` | `#2B3231` / `#ECF2F0` | `#DDE4E2` / `#2B3231` | Snackbars |

Rules:

- **No dynamic colour** (`dynamicColorScheme`). The palette and the marker contrast must be the same on every phone. Revisit only with a measured reason.
- **Warm accents** `#FFC861` (light) and `#FFF3DC` (cream) belong to the icon and illustrations only. They are not used for markers or statuses (they merge with map roads).
- **Contrast.** Text ≥ 4.5 : 1, graphics and controls ≥ 3 : 1 (US-111). Measured in the mockups: light markers 5.1–5.9 : 1 on the map.
- **Status is never colour alone** (section 6.1).

### 2.2 Semantic colours outside the M3 scheme

Defined once in `Color.kt` and used for map markers only:

| Name | Value | Use |
|---|---|---|
| `markerFillLive` | `primaryLight` | Live and recent members |
| `markerRing` | `#FFFFFF` | Ring around every pin |
| `markerFillStale` | `surfaceContainerHighestLight` | Stale, offline, approximate, no position |
| `markerOutlineStale` | `outlineLight` | Dashed ring of a stale member and the big dashed circle of an approximate one |

### 2.3 Map markers ignore the UI theme

The basemap stays light in both themes (OpenFreeMap Liberty; a dark style is not introduced, no ADR needed). Member markers, their rings, accuracy circles and freshness labels therefore use **light-scheme values in light and dark UI alike**. In the first dark mockups the light-teal dark marker had a contrast of 1.36–1.56 : 1 against the map; with light values it is the same 5.1–5.9 : 1 as in the light theme. Everything around the map (group switcher, SOS button, sheet, banners) follows the UI theme.

### 2.4 Problems that are not errors

Self-check problems (battery optimisation on, manufacturer restrictions) are neutral, not red: the icon sits on a `tertiaryContainer` (or amber) circle and carries a text reason and a "Fix" button, while a healthy item has a check on a `primaryContainer` circle. Do not distinguish them only by the shape of the glyph.

## 3. Typography

- The **default Material 3 type scale** with the system font (Roboto on most phones), see [`theme/Type.kt`](theme/Type.kt). No custom fonts: smaller APK, no licensing, the font the person set in the system.
- All sizes are in `sp`; layouts are checked at **200 % font scale** (US-111) and must scroll rather than clip.

| Style | Size / line | Typical use (guidance; confirm against the frames at implementation) |
|---|---|---|
| `headlineSmall` | 24 / 32 | Screen headline on onboarding and self-check summary |
| `titleLarge` | 22 / 28 | Top bar title, member name in the card |
| `titleMedium` | 16 / 24 (medium) | List row title, group name |
| `bodyLarge` | 16 / 24 | Body text, consent and disclosure texts |
| `bodyMedium` | 14 / 20 | Secondary line of a list row (status, freshness) |
| `labelLarge` | 14 / 20 (medium) | Buttons |
| `labelMedium` | 12 / 16 (medium) | Marker labels, chips |

Numbers and units are separated by a **no-break space** in string resources (`12 s`, `3 min`, `82 %`), so a wrap never splits "12 / ago".

## 4. Shape, spacing and touch

- Material 3 shapes: large rounded containers (cards, sheets), full-round buttons and chips. Screen margin 16 dp; frames are designed at 360 × 800 dp.
- **Touch targets ≥ 48 dp.** In Compose use `minimumInteractiveComponentSize()`; tonal "Fix" buttons (≈ 40 dp in the mockups), text links and map labels must be enlarged to 48 dp in code. A marker's tap area is ≥ 48 dp and its label belongs to that area.
- Edge-to-edge; the sticky bottom zone (buttons, notes) is outside the scroll area.

## 5. Components

Compose Material 3 components the mockups are built from. Custom components live in `core:designsystem` (basic) or `core:ui` (domain), see [android.md](../architecture/android.md).

| Mockup element | Compose | Notes |
|---|---|---|
| Main / secondary action | `Button`, `OutlinedButton` | Equal size for "Continue" / "Not now", "Join" / "Decline" (no dark patterns) |
| "Fix", "Pause" on a tinted card | `FilledTonalButton`, `OutlinedButton` | |
| Links ("I have an invitation code", "How to do this on Samsung") | `TextButton` | 48 dp area |
| Top bar | `TopAppBar` (small) | Back arrow on every sub-screen |
| Group switcher, member name chip | `AssistChip` / custom pill | One tap to switch (US-035) |
| Bottom sheet over the map | `ModalBottomSheet` / standard `BottomSheetScaffold` | Map has bottom padding so markers are not hidden |
| List rows | `ListItem` | Leading avatar or icon, two-line text, trailing value |
| "You are sharing" card | `Card` filled with `primaryContainer` | Always visible on the map screen |
| Switch / radio / check | `Switch`, `RadioButton`, `Checkbox` | Age and terms: two unticked checkboxes, no pre-filled state |
| Snackbar | `Snackbar` (inverse surface) | "Paused · Change duration" |
| Dialog | `AlertDialog` | Account deletion confirmation: the destructive button is not primary |
| **Member marker** | custom | 44 dp pin with initial, white ring, accuracy circle; stale: dashed ring on `markerFillStale`; approximate: big dashed circle instead of a point |
| **Marker label** | custom | Name plus short status, `labelMedium` on `surfaceContainerHighest` |
| **SOS button** | custom | Hold ≈ 2 s; light theme uses the saturated `error` container (see 6.4) |
| **SOS banner** | custom | `error` background, `onError` text, buttons "Call", "Show", "112" |
| **Status chip, freshness text, avatar** | `core:ui` | Shared by map, list and card |

Icons: Material Symbols as in the kit; the kit has no battery, warning or SOS glyphs, so a battery icon (level, bolt while charging) and an `error` glyph on a neutral circle are added; SOS is a text mark, not an icon.

## 6. Rules every screen follows

### 6.1 Freshness and device state (BR-08, US-031)

The server computes the freshness status; the UI renders it as **text plus an icon, colour is secondary**.

| Status | Rule (BR-08) | Marker | Row text example |
|---|---|---|---|
| Live | position age ≤ 2 min | solid pin, thin accuracy ring | "At school · arrived 08:12" |
| Recent | last contact within 1.5 × expected interval | solid pin | "At home since 07:40 · checked 3 min ago" |
| Stale | 1.5–4 × expected interval | dashed ring, neutral fill | "No updates for 25 min" |
| Offline | beyond 4 × | dashed ring, neutral fill | "Not in contact since 09:12" |

Device-reported states are shown **on top** of freshness as badges with text: **Paused**, **Location off**, **No permission**, **Limited** ("only while the app is open"), **Power saving / low battery**, **Approximate**.

- **Position age and last contact are different things** (US-031 AC2): "here since 08:10" and "checked 3 min ago" are separate lines in the card and the list.
- **Never show old as fresh** (P5): no animated movement between points; when no fresh point arrives, say "Could not get a fresh location. Last seen 12 min ago."
- **Marker label** shows what the first part of the list status shows: for a person standing still "Taras · at home" or "Taras · since 07:40", for a moving person the age of the point ("Taras · 3 min ago" only if that is the point's age). "Checked N min ago" appears only in the list and the card. At 200 % font, labels show the name only; freshness stays in the list and the card.
- **Labels do not cover markers.** On conflict show the name only, shift the label or cluster; the map has padding for the sheet and floating buttons so markers do not hide under them. An approximate marker stays distinguishable.
- **Battery** is an icon (level, bolt while charging) with a content description ("Battery 82 %, not charging"); low battery is icon plus text, never the number alone.

### 6.2 Sharing, pause and stop

- The state "I am sharing / Paused / Not sharing" is **always visible on the map screen** and in the privacy hub.
- **Pause is one tap** (store compliance K7, P1): tapping "Pause" pauses immediately ("until I resume") and a snackbar shows "Paused · Change duration" with the options 1 h · until tomorrow 08:00 · until I resume. The same applies in the privacy hub.
- While I am paused, others' positions are hidden (banner "Resume sharing to see others"); names, statuses and battery stay. A member's SOS is still shown (US-052).

### 6.3 Visible requests (BR-04, US-028)

A fresh-location request is never silent. In the member card the note "{Name} will be notified that you asked for their location, and the request appears in their access log" always sits **in the sticky zone above "Update location"**, also at 200 % font (shortened to "{Name} will be notified of the request"). The refresh button never fakes success: "Asking…" shows the real age, a timeout after 60 s shows "Could not get a fresh location. Last seen 13 min ago."

### 6.4 SOS (US-050, US-052, BR-07)

- The SOS button is easy to find and hard to press by accident: **press and hold ≈ 2 s**; a short tap shows a hint. In the **light theme the container is the saturated `error` colour** (as in dark), because a pink container on the light map has a boundary contrast of 1.16 : 1; alternatively add an `error` outline.
- A member's SOS shows a banner on top of everything, also to a person who is paused, with the live position, "Call {Name}", "Show" and "Call 112" (content description "Call 112").
- **My SOS is active** needs its own state: "SOS is active — your location is sent to your groups", "I am safe", "Cancel SOS" (not drawn; see section 7).
- Notifications about SOS cannot be switched off in the app; the privacy hub says "SOS is always on".

### 6.5 Build flavours (BR-11, US-070, US-071)

The `store` build has no operator features; the `internal` build shows them. Texts differ and nothing operator-related appears, even empty, in `store`:

| Where | `store` | `internal` |
|---|---|---|
| Access log header | "Only you can see this log" | "You and the test-version operator can see this log" |
| Who sees what about me | groups and place subscriptions | + a row for the operator |
| Log entries | "Olena asked for your location · 14:32" | + "The operator asked for your location", "The operator viewed the live map (once a day)" |
| Onboarding | no operator step | an operator capabilities step (US-090) |

### 6.6 Invitations and consent

- The consent text of the invitation is shown **in full** before "Join" (permissions-rationale section 4.6); "Decline" is as prominent as "Join".
- The decline note must be true: "If you decline, {Name} sees that the invitation was declined. The record is deleted after 30 days." (the invitation record is kept for 30 days, see [data inventory](../privacy/data-inventory.md)).
- Invitation codes are read aloud over the phone. The input field **normalises Cyrillic homoglyphs of Latin letters to the Latin ones** (those of A, B, C, E, H, K, M, P, T, X) and case; the alphabet excludes `0/O`, `1/I/L` and `W`; the spoken hint is built to match. The button is called the same everywhere: "I have an invitation code" (onboarding summary, empty map, web page).
- The invitation web page for a beta build points to an APK or install instructions; the Google Play button is for the release page only (J-03).

### 6.7 Navigation

The avatar on the map opens a menu with explicit items **Privacy**, **Places**, **Account** (a bottom bar is the alternative; decide in the navigation spec). The privacy hub is two taps from the map, the policy and terms are at most two taps from the hub (US-073).

### 6.8 Accessibility (US-111)

- Contrast, touch targets and 200 % font as above; status not by colour alone.
- Every marker, chip, battery and self-check row has a content description in the form "Location permission: all good" / "needs attention"; the "Asking…" button announces "Request in progress".
- The "Always allow" hint on onboarding is an **illustration**, not working radio buttons: TalkBack reads "Hint: choose «Allow all the time»" and it never imitates the system settings screen.
- At 200 % the "Invite" action moves to the header menu or aligns left instead of wrapping.

## 7. States the mockups do not draw

The mockups cover the main states; the rest are described here and implemented to these rules (full requirement text: [PRD](../product/prd.md), journeys: [user-journeys.md](../product/user-journeys.md)).

### 7.1 Member card (US-028…US-034)

| State | What is shown |
|---|---|
| Normal / live | All fields; "Update location" enabled |
| Asking | Current position with its real age + "Asking for a fresh location…"; non-blocking progress |
| No fresh point after 60 s | "Could not get a fresh location. Last seen 12 min ago." No fake update |
| Target is paused | "Update" disabled with the reason "Paused since 14:05" (not grey without explanation) |
| Request limit (BR-12) | "You asked recently — try again in a minute"; neutral, not red |
| No position yet | "No location yet" |
| I am paused | "Resume sharing to see the location"; status and battery stay (BR-02) |
| Target without permission / location off | Status + last position with its age |
| Low battery | "Battery low (12 %) — updates are less frequent" (US-025 AC3) |

### 7.2 Map (US-023, US-030)

| State | What is shown |
|---|---|
| Loading | List skeleton, map placeholder; never a full-screen spinner |
| Offline (no network on my phone) | "No connection. Showing last known locations (from HH:MM)"; ages count from the last update, not from now |
| Tiles failed | Member list with statuses and ages still works; neutral "Map not loaded" in place of the map |
| No permission on my phone | Like "not sharing" + a reliability banner (amber, not red) with "Fix" |
| Server error | "Could not refresh. We will try again." + last data with its age |
| My SOS active | See 6.4 |

### 7.3 Privacy and invitations

| Screen | State | What is shown |
|---|---|---|
| Hub | Not sharing | "No group can see your location" |
| Access log | Empty | "No requests yet" |
| Who sees me | No subscriptions | "Nobody is notified when you arrive or leave" |
| Hub | Offline | Cached data marked "updated at HH:MM" |
| Account | Delete | Two steps: what is removed now, what within 30 days; the "Delete" button is not primary |
| Pause | Choice | 1 h · until tomorrow 08:00 · until I resume; automatic resume is more reliable with the battery exception |
| Invitation | Invalid | Expired, used or revoked: a clear message + "Ask for a new invitation"; nothing changes |
| Invitation | Many attempts | "Too many attempts. Try later"; no hints whether a code exists |
| Invitation | Already in the group | "You are already in this group" → "Open" |

History ("Who sees my history") is v1.1 and is not drawn; the hub keeps room for it.

## 8. Icon

Concept: **a window with a light** — an arched window of four warm panes and a sill on the sea-green background. It is not a pin, expresses the name and stays legible at 48 px.

| File | Content |
|---|---|
| [`icon/ic_launcher_foreground.svg`](icon/ic_launcher_foreground.svg) | Foreground layer, 108 × 108 dp, everything inside the 66 dp safe circle (radius 33) |
| [`icon/ic_launcher_background.svg`](icon/ic_launcher_background.svg) | Background layer, solid `#1B7F79` |
| [`icon/ic_launcher_monochrome.svg`](icon/ic_launcher_monochrome.svg) | Monochrome layer (Android 13+), same drawing in one colour |
| [`icon/icon-full.svg`](icon/icon-full.svg) | Both layers together, 512 × 512, source of the Google Play PNG |

The SVGs are converted to Android vector drawables (Android Studio *Image Asset*) when the project is created. Before release check the layers against the [adaptive icon](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) and [Google Play icon](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) requirements, export a 512 px PNG and look at the icon on a real phone. The icon and the persistent notification are never hidden (store compliance K1–K11).

## 9. Theme code

[`theme/`](theme/) holds `Color.kt`, `Theme.kt` and `Type.kt` in the structure of the Material Theme Builder Compose export, with light and dark schemes and no contrast variants. The files are moved into `core:designsystem` when the Android project is created (walking skeleton) and the package `core.designsystem.theme` is replaced with the real one. `AppTheme` does not use dynamic colour. Medium and high contrast schemes are added if the product needs them (they are not in the requirements).

## 10. Mockup index

Exported frames at 2× (720 × 1600) in [`screens/`](screens/), light theme unless named otherwise. Only fictional people (Olena, Maks, Halyna, …) appear; the map is an OpenFreeMap tile sample.

| Screen | Frames |
|---|---|
| Onboarding | [disclosure](screens/01-onboarding-disclosure-light.png) · [disclosure, dark](screens/01-onboarding-disclosure-dark.png) · [disclosure, 200 %](screens/01-onboarding-disclosure-200-light.png) · [Always allow](screens/01-onboarding-allow-all-the-time-light.png) · [summary](screens/01-onboarding-summary-light.png) |
| Group map | [normal](screens/02-map-normal-light.png) · [normal, dark](screens/02-map-normal-dark.png) · [normal, 200 %](screens/02-map-normal-200-light.png) · [empty](screens/02-map-empty-light.png) · [my pause](screens/02-map-my-pause-light.png) · [change pause length](screens/02-map-pause-change-duration-light.png) · [member SOS](screens/02-map-sos-light.png) |
| Member card | [normal](screens/03-card-normal-light.png) · [normal, dark](screens/03-card-normal-dark.png) · [normal, 200 %](screens/03-card-normal-200-light.png) · [asking](screens/03-card-requesting-light.png) · [no fresh location](screens/03-card-no-fresh-location-light.png) |
| Invitations | [create group](screens/04-invite-create-group-light.png) · [invitation created](screens/04-invite-created-light.png) · [accept with consent](screens/04-invite-accept-consent-light.png) · [web page](screens/04-invite-web-page-light.png) |
| Privacy | [hub](screens/05-privacy-hub-light.png) · [hub, dark](screens/05-privacy-hub-dark.png) · [reliability check](screens/05-privacy-reliability-check-light.png) · [access log](screens/05-privacy-access-log-light.png) |

The remaining dark frames and further states (pause variants, onboarding summary with limited or no permission, member on pause, roles in an invitation, "Who sees me") exist in the Figma file.

## 11. Deviations and open points

- **Teal kit mode instead of the exact scheme.** The free Figma plan does not allow adding a variable mode, so the frames use the kit's *Teal* mode (primary `#006B60`, error `#B3261E`). The code uses the exact scheme in `theme/` (primary `#006A65`, error `#BA1A1A`). The difference is minimal; screenshot tests are authoritative.
- **Marker captions and 200 % font.** Marker captions show only the name at 200 %; the staleness is visible by the dashed ring and in the list (accepted).
- **Monogram size.** Initials in pins and avatars do not scale with the font (their size is in dp).
- **Not drawn:** my SOS active state, SOS hold hint, Places (J-07), the Account screen, battery icon in the list, a dedicated "member on pause while I am paused" frame, the navigation menu. Each is drawn or specified together with its feature spec.
- **Open:** battery as text "82 %" in the mockups (the icon is in 6.1); the map-style question for a future dark basemap; plan B seed check on a map with parks and water; Google Play name search.

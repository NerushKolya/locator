# Personas

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: [vision](vision.md) · [user journeys](user-journeys.md) · [PRD](prd.md)

Personas are fictional composites. They describe needs and fears that drive requirements; each persona lists the user stories ([PRD](prd.md)) that exist mainly because of them.

All people are 13 or older, have their own Android phone with Google Play services and their own Google account, and live in Ukraine.

---

## 1. Olena — the organizer (primary)

| | |
|---|---|
| Age, role | 44, accountant, mother of two; creates and owns the family group |
| Phone | Recent mid-range Samsung, Android 15 |
| Tech comfort | Confident everyday user; installs apps herself; will not dig through system settings without instructions |

**Goals**
- Glance at the map and know that her husband, her son and her mother are fine.
- Get told when her son gets home from school without having to ask him.
- Help her mother set the app up once and not worry about it again.

**Frustrations and fears**
- A dot that "sits" in one place for hours and she cannot tell whether the phone died or the person is really there.
- Apps that drain the battery — her son will just turn them off.
- Long setup with confusing permission screens.

**What she needs from us**
- Freshness and status she can read at a glance; a clear reason when someone is silent.
- Places ("Home", "School") with arrival/departure alerts she subscribes to.
- An invitation flow simple enough to do over the phone with her mother.

**Key stories:** US-010–US-017 (groups), US-030–US-035 (map), US-040–US-043 (places), US-061 (no-updates alert).

---

## 2. Maks — the teenager (primary)

| | |
|---|---|
| Age, role | 15, high-school student; member of the family group and of a friends' group |
| Phone | Gaming phone, Android 15, aggressive power management |
| Tech comfort | High; notices everything the app does in the background |

**Goals**
- Let his parents see he is OK without constant "where are you?" messages.
- Share with friends when they meet up.
- Keep some control: pause for a while without a fight.

**Frustrations and fears**
- Feeling watched by someone who does not share back.
- Not knowing who is getting alerts about him or who is checking his position.
- Battery drain during the school day.

**What he needs from us**
- Reciprocity: only people who share see him (P4 in [vision](vision.md)).
- A visible pause with an optional timer, so everyone sees "paused until 18:00" instead of "phone died".
- The "Who sees what about me" screen: groups, alert subscribers, location requests.
- A visible notification whenever someone requests his fresh location.

**Key stories:** US-022 (pause), US-023 (reciprocity), US-028 (fresh location request), US-070–US-072 (transparency, access log, self-check).

---

## 3. Halyna — the grandmother (secondary)

| | |
|---|---|
| Age, role | 69, retired; lives alone in another city; member of the family group |
| Phone | Older budget phone, Android 10–11, small storage, power saving often on |
| Tech comfort | Low; uses calls, a messenger and the camera; large font size in system settings |

**Goals**
- Let her daughter know she is fine without calling several times a day.
- Get help quickly if something happens.

**Frustrations and fears**
- Many screens and buttons; English words; anything that looks like a warning.
- Notifications she does not understand.

**What she needs from us**
- Setup that a relative can guide over the phone, in Ukrainian, with large touch targets and support for large system fonts.
- An SOS button that is easy to find and hard to press by accident, plus a direct "Call 112" option.
- Graceful behaviour on old Android versions and in power-saving mode, with the status visible to her family.

**Key stories:** US-020 (onboarding), US-050–US-052 (SOS), US-060 (low-battery alert to family), US-110 (localization), US-111 (accessibility).

---

## 4. Andrii — the operator (internal, development and beta only)

| | |
|---|---|
| Age, role | 38, the developer and owner of the project; also a regular member of his own family group |
| Phone | Two test phones, Android 15 |
| Tech comfort | Backend developer; new to Android |

**Goals**
- Find out quickly why a family member's phone stopped sending points.
- Request a fresh location or turn sharing on remotely while debugging during the beta.
- Change location-engine parameters without releasing a new app version.

**Constraints**
- These capabilities exist only in development and beta builds, are disclosed to every beta participant before installation and are visible to the affected person in the access log ([vision §8](vision.md#8-operator-capabilities-in-development-and-beta-builds)).

**Key stories:** US-090–US-095 (operator), US-072 (self-check data as diagnostics).

---

## 5. Ira — the friend (secondary, v1.1+)

| | |
|---|---|
| Age, role | 27, designer; in a friends' group for trips and evenings out; also in her own family group |
| Phone | Recent Pixel, Android 16 |

**Goals and needs**
- Different sharing in different groups: always with family, only during a trip with friends (per-group sharing switch, v1.1).
- Later: show only an approximate area to some groups (v2).

**Key stories:** US-013 (multiple groups), US-084 (per-group sharing, v1.1), US-130 (approximate location, v2).

---

## 6. Anti-persona — the controlling partner

We design **against** this person. A controlling partner wants to track another adult without their knowledge or real agreement: install the app on someone else's phone, hide it, turn sharing on remotely, read the history secretly.

| What they would try | What stops them |
|---|---|
| Install and configure on someone else's phone | Onboarding requires the person's own Google account, own consent screen and own system permission dialogs |
| Hide the app or the notification | Icon and Android persistent notification are never hidden (P2) |
| Watch without sharing back | Reciprocity rule (P4) |
| Request location silently | Every request shows a notification on the target phone and appears in the access log |
| Subscribe to alerts secretly | Alert subscribers are listed on the target person's transparency screen |
| Turn sharing on remotely | Not possible in the Google Play build; in development/beta builds only the operator can, it is disclosed in advance and logged |
| Read history | History is visible only to its owner by default (v1.1); sharing it is a separate explicit choice |

The anti-persona is a test input for every new feature: "Could this help someone track another adult without their knowledge?" If yes, the feature changes or is dropped.

# Contract: structured log records and scrubbing

**Feature**: [spec.md](../spec.md) (FR-020) · **Plan**: [plan.md](../plan.md) · Principle V of the project constitution · [backend §10](../../../docs/architecture/backend.md)

Applies to `core` (PHP), `delivery` (Go) and the app (Kotlin). Each implementation has automated tests that log every vector of §4 and assert that no forbidden value appears in the written output.

## 1. Record format

One JSON object per line, UTF-8.

| Field | Required | Meaning |
|---|---|---|
| `ts` | yes | RFC 3339 UTC timestamp with milliseconds |
| `level` | yes | `debug`, `info`, `warning`, `error` |
| `service` | yes | `core`, `core-migrate`, `delivery`, `app` |
| `msg` | yes | Short constant message; variable data goes into fields |
| `requestId` | in requests | From `X-Request-Id` if it matches `^[A-Za-z0-9-]{8,64}$`, else generated (UUID v7); echoed in the response header |
| `event` | no | Stable event name, e.g. `http.request`, `config.served` |
| other fields | no | Allow-listed per event; values are scalars |

Server request records carry `method`, route (not the raw path with identifiers once routes have identifiers), `status`, `durationMs`. Health requests are logged at `debug` only.

In the app the same object is written as the logcat message with tag `Locator`; release builds drop `debug` records.

## 2. Scrubbing rules

Applied to the message and to every field value, recursively, before the record is written.

**Layer 1 — by key** (case-insensitive, matched on the last key segment): the value is replaced by `"[redacted]"`.

| Kind | Keys |
|---|---|
| Coordinates | `lat`, `lon`, `lng`, `latitude`, `longitude`, `coordinates`, `location`, `position`, `accuracyM` |
| Place names | `placeName`, `place`, `address` |
| Tokens and secrets | `token`, `accessToken`, `refreshToken`, `idToken`, `pushToken`, `ticket`, `authorization`, `password`, `secret`, `apiKey`, `nonce` |
| E-mail addresses | `email`, `e-mail`, `mail` |
| Invitation codes | `code`, `invitationCode`, `inviteCode` |

**Layer 2 — by pattern** inside any string value: the match is replaced by `[redacted:<kind>]`.

| Kind | Pattern (informal) |
|---|---|
| `coords` | Two decimal numbers with at least 3 fraction digits separated by a comma and optional spaces, each within ±180 (e.g. `50.4501, 30.5234`) |
| `token` | JWT-like `eyJ…` with two dots; `Bearer <anything up to whitespace>` |
| `email` | `local@domain.tld` |
| `invite` | 8 characters of the invitation alphabet directly after `code`, `invite` or `invitation` followed by `:`, `=` or a space |

Place names have no recognisable pattern; they are allowed only under the keys of layer 1 and therefore never reach the output.

## 3. What is not scrubbed

User ids, group ids, request ids, HTTP status codes, durations, version numbers, counts. These are needed for diagnosis and are not personal location data.

## 4. Test vectors

Each implementation logs these inputs (message and context) and asserts on the written line.

| # | Input | Forbidden in output |
|---|---|---|
| 1 | field `lat: 50.4501`, `lon: 30.5234` | `50.4501`, `30.5234` |
| 2 | message `position 50.4501, 30.5234 accepted` | `50.4501`, `30.5234` |
| 3 | field `placeName: "Home"` | `Home` |
| 4 | field `accessToken: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"` | the token |
| 5 | message `Authorization: Bearer abc.def.ghi failed` | `abc.def.ghi` |
| 6 | field `email: "person@example.org"` and message `sent to person@example.org` | `person@example.org` |
| 7 | field `invitationCode: "K7P2M9QX"` and message `invite code K7P2M9QX expired` | `K7P2M9QX` |
| 8 | nested field `device: { location: { lat: 50.45, lon: 30.52 } }` | `50.45`, `30.52` |
| 9 | field `userId: "0192f3e4-…"`, `status: 200` | nothing — values stay |

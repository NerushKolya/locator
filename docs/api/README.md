# API guide

> Status: draft for blueprint review · Last updated: 2026-11-15
> Related: contract — [`api/openapi.yaml`](../../api/openapi.yaml) · [realtime protocol](realtime-protocol.md) · [backend](../architecture/backend.md) · [security](../architecture/security.md) · [ADR-009](../architecture/adr/ADR-009-contract-first-openapi.md)

## 1. Where the contract lives

| Artifact | Location | Purpose |
|---|---|---|
| REST contract | `api/openapi.yaml` (OpenAPI 3.1) | Source of truth for the app ↔ core API. Linted and checked for breaking changes in CI |
| Stream protocol | [realtime-protocol.md](realtime-protocol.md) | Events of `/v1/stream` (Server-Sent Events) |
| Admin API contract | `api/admin-openapi.yaml` | Added when the back office is built; internal network only |
| This guide | `docs/api/README.md` | Conventions that every endpoint follows |

The contract is changed first, then the code ([ADR-009](../architecture/adr/ADR-009-contract-first-openapi.md)).

## 2. General conventions

- **Base path and versioning.** All endpoints are under `/v1`. Within a version only backward-compatible changes are made: new endpoints, new optional request fields, new response fields, new enum values in responses where the field is documented as open. A breaking change means `/v2` running side by side until old app versions are gone.
- **Clients must ignore unknown response fields and unknown event types.** The server rejects unknown request fields (`422`), so typos never pass silently.
- **Format.** JSON, UTF-8, `camelCase` field names. Units are in the name: `accuracyM`, `ageMs`, `nextReportInS`, `speedMps`.
- **Time.** RFC 3339 in UTC (`2026-11-15T16:20:31Z`). Durations are integers with a unit suffix. Every response that the app uses to show ages contains `serverTime`; the app computes "N min ago" against the server clock, not the phone clock.
- **Identifiers.** UUIDs. Points and SOS episodes get their ids on the phone; everything else on the server.
- **Coordinates.** WGS 84, `lat` and `lon` as decimal degrees.
- **Compression.** Requests may use `Content-Encoding: gzip` (device reports do); responses are compressed on request.
- **Localization.** The API returns data, codes and identifiers, never user-facing sentences. Texts are built in the app.

## 3. Authentication

| Credential | Header | Lifetime | Used for |
|---|---|---|---|
| Access token | `Authorization: Bearer <jwt>` | 15 min | All `/v1` endpoints except `/v1/auth/*` (public) and `/v1/stream` |
| Refresh token | Request body of `/v1/auth/refresh` | 60 days sliding, rotated at every use | Getting a new token pair |
| Stream ticket | `Authorization: Bearer <ticket>` | 60 s, single use | Opening `/v1/stream` |

Client rules:

1. On `401 unauthorized` refresh once and retry; on `401 session_revoked` stop sharing, clear local data and show the sign-in screen (with "Signed in on another device" when the push said so).
2. Only one refresh may run at a time; concurrent requests wait for it. Sending an old refresh token twice revokes the session.
3. Tokens are never placed in URLs or logs.

## 4. Errors

All errors use `application/problem+json` (RFC 9457):

```json
{
  "type": "https://<host>/problems/target-paused",
  "title": "The member has paused sharing",
  "status": 409,
  "code": "target_paused",
  "requestId": "01JCQ7W2ZK3M5V8N4T6R9Y0A1B"
}
```

- `code` is the stable, machine-readable part; clients branch on it, never on `title` or `detail`.
- `requestId` is also returned in the `X-Request-Id` response header and appears in server logs.
- `errors[]` lists invalid fields for `validation_failed`.
- A resource the caller may not know about is `404 not_found`, the same as a missing one.

| HTTP | `code` | Meaning |
|---|---|---|
| 401 | `unauthorized` | Missing, malformed or expired access token |
| 401 | `session_revoked` | Session ended: sign-out, new device, token reuse, account deletion |
| 401 | `invalid_id_token` | Google ID token failed verification |
| 403 | `forbidden` | Role does not allow the action |
| 403 | `operator_mode_off` | Operator capability requested while operator mode is off (admin API) |
| 404 | `not_found` | Missing or not accessible |
| 409 | `confirmation_required` | New account needs age and terms confirmation |
| 409 | `already_member` | The caller is already in the group |
| 409 | `group_full` | Member limit reached |
| 409 | `group_limit_reached` | The user is in the maximum number of groups |
| 409 | `place_limit_reached` | Place limit of the group reached |
| 409 | `owner_must_transfer` | The owner must transfer ownership or delete the group before leaving |
| 409 | `viewer_not_sharing` | Reciprocity: the caller must share to do this |
| 409 | `target_not_sharing` | The member has not turned sharing on |
| 409 | `target_paused` | The member is paused |
| 409 | `sos_already_active` | Another SOS of the user is active |
| 410 | `invitation_unavailable` | Invitation expired, used or revoked |
| 413 | `payload_too_large` | More than 200 points or 256 KB |
| 422 | `validation_failed` | Invalid body or parameters |
| 429 | `rate_limited` | Limit exceeded; see `Retry-After` |
| 500 | `internal_error` | Unexpected error; no details exposed |
| 503 | `unavailable` | Temporary; retry with back-off |

New codes may be added; a client treats an unknown code by its HTTP status.

## 5. Idempotency and retries

| Operation | Mechanism |
|---|---|
| Device report (`POST /v1/me/reports`) | Each point has a phone-generated `pointId`. A repeated point is listed in `accepted` again and stored once. The whole request can be retried blindly |
| Start SOS (`PUT /v1/me/sos/{sosId}`) | The id is generated on the phone; repeating the request returns the same SOS |
| Sharing change (`PATCH /v1/me/sharing`) | Revision counter `rev`; a change with a revision not higher than the stored one is ignored |
| Creating a group, invitation or place | Optional `Idempotency-Key` header; the first response is replayed for 24 h |
| `PUT` and `DELETE` endpoints | Naturally idempotent |
| Accepting an invitation | Repeating after success returns the same group |

Retry policy for clients: retry on network errors, `429`, `502`, `503`, `504` with exponential back-off and jitter, honouring `Retry-After`; never retry other `4xx` without changing the request. The location engine's upload queue has its own back-off (5 s … 5 min).

## 6. Pagination

Lists that can grow (`/v1/me/access-log`; history in v1.1) use cursor pagination:

- Request: `?limit=50&cursor=<opaque>`; `limit` 1–100.
- Response: `{ "items": [...], "nextCursor": "<opaque>" | null }`; newest first.
- Cursors are opaque strings valid for at least an hour.

Bounded lists (groups ≤ 10, members ≤ 20, places ≤ 20, invitations) are returned whole as `{ "items": [...] }`.

## 7. Caching and conditional requests

- `GET /v1/config` supports `ETag` / `If-None-Match`; device report responses carry `configVersion` so the app knows when to refetch.
- All other responses are `Cache-Control: no-store`: they contain personal or location data.

## 8. Rate limits

Limits are listed in [security.md §6](../architecture/security.md). A limited request gets `429 rate_limited` with `Retry-After` (seconds). Limits are per user, per device, per target or per IP address depending on the endpoint.

## 9. Visibility rules in responses

The API never asks the client to hide data; it does not send it.

- `GET /v1/groups/{groupId}/snapshot` returns for each member a `positionVisibility`: `visible`, `last_known`, `hidden_viewer_not_sharing` or `none`. `position` is present only for the first two.
- A viewer who does not share receives no positions through any endpoint or stream, except members with an active SOS.
- Fresh-location requests fail with an explicit code when reciprocity or pause forbids them.
- Position objects always carry `fixedAt` (position time); member objects carry `lastSeenAt` (last contact) and `freshness`. Clients show both clocks and never derive freshness categories themselves.

## 10. The device report in detail

`POST /v1/me/reports` is the phone's single write path for location.

| Part | Content |
|---|---|
| `points[]` | 0–200 points. Each: id, sequence number, coordinates, accuracy, optional altitude/speed/bearing, device time, **age in ms on the monotonic clock**, time source, engine mode, battery, flags |
| `status` | Next report promise (`nextReportInS`), engine mode, sharing state with revision, battery, location enabled, permission level, precision, power saving, battery-optimization exemption, app version, self-check summary |
| `ackCommandIds[]` | Commands already applied |

Response: `accepted[]`, `rejected[]` (with reason `invalid`, `too_old` or `not_sharing`), `commands[]`, `configVersion`, `serverTime`.

Server behaviour: position time = receive time − `ageMs`; the unique point id makes re-sent points harmless; the current position moves only forward in time; a report without points is a heartbeat; points recorded while the person is not sharing are refused (`not_sharing`), unless an SOS is active — points recorded before a pause began (position time earlier than the pause start) are accepted into history but never move the live marker of a paused person.

Commands:

| `type` | Meaning | Fields |
|---|---|---|
| `active_view` | Report frequently for a lease period; show who is viewing | `leaseS`, `viewerUserIds` |
| `locate` | Answer a fresh-location request; show who asked | `requestId`, `requesterKind`, `requesterUserId` |
| `resume_sharing` | A timed pause has ended on the server; resume if the phone has not resumed by itself | `pauseRev` |
| `refresh_config` | Fetch `/v1/config` | — |
| `refresh_push_token` | Re-register the push token | — |
| `start_sharing` | Operator's remote turn-on (internal flavor and operator mode only) | — |

## 11. Web pages served by the core

Not part of the JSON API, but on the same host:

| Path | Purpose |
|---|---|
| `/i/{code}` | Invitation landing page: opens the app through a verified app link, or explains how to install it and shows the code |
| `/.well-known/assetlinks.json` | App-link verification |
| `/privacy`, `/terms` | Privacy policy and terms at stable URLs |
| `/account/delete` | Web account deletion (store release) |

## 12. Changing the API

1. Edit `api/openapi.yaml` (and this guide or the realtime protocol when conventions change).
2. The linter and the breaking-change check must pass.
3. Implement server and client; contract tests validate real responses against the file.
4. Update affected product and architecture documents in the same change.

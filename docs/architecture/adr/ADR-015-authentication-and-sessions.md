# ADR-015: Authentication with Google ID tokens and own session tokens

- Status: Proposed (sign-in method itself is accepted)
- Date: 2026-11-15

## Context

Users sign in with Google through Credential Manager (US-001). The app must stay signed in for months (US-003) while a revoked session — sign-out, a new device, account deletion — must stop working at the phone's next contact. One device is active per account (BR-10). `delivery` must authenticate streams without access to session data. Tests cannot obtain real Google tokens.

## Options

1. **Use Google ID tokens as the API credential.** No own tokens. Short-lived, tied to Google's availability, no revocation of our own, no device binding.
2. **Opaque session tokens looked up in the database.** Instant revocation. Every service that authenticates needs the session store.
3. **Short-lived signed access token + rotating refresh token**, with a session check in the core.
4. **Third-party auth service (Firebase Authentication and the like).** Less code. Another vendor holding identities; more data to declare.

## Decision

Option 3.

- **Sign-in:** the app obtains a Google ID token with a server-issued nonce; the core verifies signature (Google's published keys), issuer, audience (our server client id), expiry and nonce; then finds or creates the user by the token's stable subject identifier.
- **Access token:** a JWT signed with an asymmetric key, 15 minutes, claims: user, session, device, distribution. The core additionally checks on each request that the session is not revoked — one indexed lookup — so revocation is immediate.
- **Refresh token:** 256 random bits, stored only as a hash, rotated at every use, sliding lifetime of 60 days *(config)*. Presenting a token that was already rotated revokes the session.
- **Device binding:** a session belongs to one device; a new sign-in revokes the previous device and its sessions in the same transaction.
- **On the phone:** tokens are encrypted with a key in Android Keystore, excluded from backups, never logged.
- **Streams:** separate short-lived tickets ([ADR-005](ADR-005-stream-tickets.md)).
- **Tests and local development:** a test identity provider in a bundle that is not installed in production images.

## Consequences

- Google is needed only at sign-in; existing sessions survive its outages.
- A stolen access token is useful for at most 15 minutes and only until the session is revoked; a stolen refresh token is detected at the next legitimate refresh.
- The signed token format lets another service verify tokens later without the session store, if immediate revocation is not required there.
- Key rotation needs a key id in the token header and two accepted keys during the change.
- Losing access to the Google account means losing the Locator account until a second sign-in method (e-mail code, v2) exists.

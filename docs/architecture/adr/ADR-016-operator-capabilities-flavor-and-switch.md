# ADR-016: Operator capabilities behind a build flavor and a server switch

- Status: Accepted
- Date: 2026-11-15

## Context

During development and the family beta the project owner acts as the operator and can see all users' positions and history, request a fresh location and turn sharing on remotely (BR-11, US-090–US-095). Beta participants are told before installing. Google Play does not allow apps to track other adults even with their permission ([Stalkerware policy](https://support.google.com/googleplay/android-developer/answer/9888380)), so none of this may exist in the store release, and removing it must not require rewriting the system. The final scope of operator rights for the public release is decided before publication.

## Options

1. **Run-time role check only.** One build. The code ships to the store; a bug or a compromised account could enable it.
2. **Build flavor only.** Code absent from the store app. The server could still serve operator requests to anyone holding back-office credentials.
3. **Both: a build flavor on the phone and a switch on the server**, plus mandatory logging.

## Decision

Option 3.

- **Phone:** the `internal` flavor contains the module with the operator disclosure screen and the handler for the operator's "turn sharing on" command. The `store` flavor is compiled without that module; a build check fails if operator classes appear in the store artifact.
- **Server:** operator capabilities are available only while `OPERATOR_MODE` is on (deployment configuration, off by default) **and** the target's active device reports the `internal` distribution. Otherwise the admin API answers `403`.
- **Transparency:** every operator capability writes an access-log entry for each affected person and produces a notification on their phone (BR-04). The phone applies a remote turn-on itself and always shows the persistent notification; if Android forbids the background start, nothing starts silently.
- Service administration that does not touch location (remote configuration, statistics without coordinates, blocking abusive accounts) is a separate scope and does not depend on operator mode.

## Consequences

- Removing operator capabilities for the store is a build and configuration matter, verified by tests.
- Two code paths to test: with and without the module.
- During the beta, a compromised operator account can see locations — mitigated by a second factor, the access log and the limited number of participants; this risk is the reason the capability is temporary.
- The live overview needs a rule for how often viewing is logged and notified (open question S2 in [security.md](../security.md)).

# ADR-009: Contract-first REST API with OpenAPI 3.1

- Status: Accepted
- Date: 2026-11-15

## Context

One API has several clients: the Android app, the back office's web module, `delivery` (internal calls) and later possibly iOS and a web client. Development is specification-driven, and server and clients are written in four languages, so an explicit, machine-checkable contract prevents drift between them. OpenAPI 3.2 exists but tool support is still partial; code generators differ in maturity per language.

## Options

1. **Contract-first:** edit `api/openapi.yaml`, then implement and verify code against it.
2. **Code-first:** generate the specification from server code (for example API Platform). Less writing; the contract follows the implementation, and the framework dictates structure.
3. **No formal contract.** Fastest start; errors found on the phone.

## Decision

Contract-first with OpenAPI 3.1 in `api/openapi.yaml`.

- The specification is linted in CI and checked for breaking changes against the main branch.
- **Server (PHP):** no generated code; requests and responses are validated against the contract in tests.
- **TypeScript:** types are generated from the contract.
- **Kotlin:** try a generator in the walking skeleton; if the output is heavy or does not support 3.1 constructs, write DTOs by hand with kotlinx.serialization and verify them with contract tests.
- **Go:** `delivery` has a tiny public surface; generation is optional.
- If a tool cannot handle a 3.1 construct, the contract is limited to the subset the tools support rather than changing the approach.
- Conventions (errors, pagination, idempotency, versioning) are in the [API guide](../../api/README.md).

## Consequences

- A change to the API starts with a reviewable diff of the contract.
- Clients and server can be developed and tested independently against the same file.
- Extra discipline: the contract must be updated in the same change as the code.
- Generator limits are discovered early, in the walking skeleton, not during feature work.
- Generator trial, walking skeleton (OpenAPI Generator 7.27.0, `kotlin` client, `jvm-ktor` library, kotlinx.serialization, run outside the build): the whole OpenAPI 3.1 contract is parsed, but the output is 164 files and about 9,500 lines, and the free-form `engine` object of `RemoteConfig` becomes `Map<String, Any>`, which kotlinx.serialization cannot encode. **Decision: do not adopt now**; the app keeps hand-written DTOs checked by contract-shaped tests. Revisit at stage 3, when the number of operations makes hand-written DTOs costly.

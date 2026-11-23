# ADR-026: Go test reports — gotestsum in the `delivery` test image

- Status: Accepted (by the owner, 2026-11-23)
- Date: 2026-11-23

## Context

CI keeps a JUnit XML report of every backend test run as an artifact ([CI](../../ops/ci.md), [testing strategy](../../quality/testing-strategy.md)). PHPUnit writes JUnit itself (`--log-junit`). `go test` writes plain text or a JSON event stream (`-json`), not JUnit. The tests of `delivery` run only inside the `test` target of `backend/delivery/Dockerfile` ([ADR-024](ADR-024-test-database-compose-profile.md)); nothing is installed on the developer PC.

## Options

1. **gotestsum** ([gotestyourself/gotestsum](https://github.com/gotestyourself/gotestsum), Apache-2.0; release v1.13.0 of 2025-09-11): runs `go test -json` and writes readable output plus a JUnit file. The most widely used tool for this job in Go projects.
2. **go-junit-report**: converts `go test -v` (or `-json`) output to JUnit; needs a pipe and keeping the exit code of `go test` by hand.
3. **A small converter written in the project**: no outside tool, but code to maintain that adds nothing to the product.
4. **No JUnit for Go**: keep the `go test -json` stream as the artifact. CI and the PHP side would report differently.

## Decision

Option 1. gotestsum is installed in the `test` stage of the `delivery` image with `go install gotest.tools/gotestsum@<version>` (an exact version, verified through the Go module proxy and checksum database), and the stage's command is `gotestsum --junitfile /reports/junit.xml -- <go test arguments>`. It is a test-time tool only: it is not in `go.mod` and not in the runtime image.

## Consequences

- `scripts/test-backend.ps1` produces `build/test-results/backend/delivery/junit.xml` the same way as the PHP report; the exit code is the exit code of `go test`.
- `go test -race` is not used in the image (it needs cgo); the race detector can be added with a cgo-enabled test stage when `delivery` gets concurrent code (stage 4b).
- Version updates are made by hand in the Dockerfile (Dependabot does not see `go install` lines); checked together with Go updates.

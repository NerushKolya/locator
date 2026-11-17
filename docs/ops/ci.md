# Continuous integration and delivery

> Status: draft for blueprint review · Last updated: 2026-11-17
> Related: [ADR-021](../architecture/adr/ADR-021-ci-github-actions.md) · [testing strategy](../quality/testing-strategy.md) · [deployment](deployment.md) · [ADR-025](../architecture/adr/ADR-025-developer-commands-powershell-scripts.md)

Workflows are added when the code they check exists: `ci.yml` with the walking skeleton (stage 1), images and deployment by stage 8, Android release by the beta.

## 1. Workflows

| File | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Pull request; push to `main` | `changes` (path filter) → `android`, `core`, `delivery`, `backoffice`, `contract`, `authz-matrix`, `e2e-backend`, `secrets` — each runs only when its paths changed; `authz-matrix` runs when `backend/core`, `backend/delivery` or `api/` changed |
| `android-instrumented.yml` | Manual; pull-request label `run-emulator`; nightly when `android/` changed since the last run | Emulator tests on API 29 and the latest API |
| `images.yml` | Tag `v*`; manual | Build `core`, `delivery`, `backoffice`, `postgres` images: `arm64` on `ubuntu-24.04-arm`, `amd64` on `ubuntu-latest`; multi-architecture manifest; push to `ghcr.io` tagged with the commit SHA and the version |
| `deploy.yml` | Manual only | Join Tailscale with an ephemeral key → SSH → steps of [deployment §5](deployment.md) → summary with the deployed tag |
| `android-release.yml` | Tag `android-v*` | Signed APK (`internal` flavor) to Firebase App Distribution for the family beta; later signed AAB (`store` flavor) to the Play internal track |
| `dependabot.yml` | Weekly | Update pull requests for Gradle, Composer, Go modules, npm, Docker base images, GitHub Actions |

## 2. Path filters

| Job | Paths |
|---|---|
| `android` | `android/**`, `api/openapi.yaml` (client generation, if used) |
| `core` | `backend/core/**`, `infra/postgres/**` |
| `delivery` | `backend/delivery/**`, `infra/postgres/**` |
| `backoffice` | `backend/backoffice/**` |
| `contract` | `api/**` |
| `e2e-backend` | `backend/**`, `api/**`, `infra/**` |
| `secrets` | Always |

## 3. Every job calls the developer scripts

| Job | Command |
|---|---|
| `android` | `pwsh scripts/test-android.ps1` (JVM unit, screenshots, lint, store-build check) |
| `core`, `delivery`, `backoffice`, `authz-matrix` | `pwsh scripts/test-backend.ps1 -Service <name>` (Compose `test` profile) |
| `contract` | `pwsh scripts/test-contract.ps1` (lint + breaking changes against `main`) |
| `e2e-backend` | `pwsh scripts/e2e-backend.ps1` |

## 4. Merge rules for `main`

- Changes reach `main` through pull requests, even when working alone: the pull request is where CI gates the change.
- Required checks: the `ci.yml` jobs that ran for the change, including `authz-matrix` and `secrets`.
- Squash merge; the pull-request title follows Conventional Commits and becomes the commit message.
- Branch protection on `main`: no force push, no deletion, required checks.

## 5. Hygiene

- `permissions: {}` at workflow level; each job requests only what it needs (`contents: read`, `packages: write` for images).
- Third-party actions pinned by full commit SHA with the version in a comment; Dependabot updates them.
- Workflow linting (zizmor) in `ci.yml`.
- Secret scanning with gitleaks on every push and pull request.
- `concurrency` with `cancel-in-progress` per branch.
- Caches: Gradle cache (read-only on pull requests), Docker layer cache per image.
- Artifacts (test reports, screenshot diffs, APKs) kept 7 days.
- Secrets used by CI: Android keystore and its passwords, Firebase App Distribution credentials, Tailscale ephemeral auth key, SSH key for the deploy user. Pull requests from forks never receive secrets.

## 6. Cost

The repository is public, so standard GitHub-hosted runners are free ([ADR-021](../architecture/adr/ADR-021-ci-github-actions.md)). If it becomes private, the monthly included usage applies; path filters, nightly emulator runs and short artifact retention keep an estimated 600–1200 minutes per month within it.

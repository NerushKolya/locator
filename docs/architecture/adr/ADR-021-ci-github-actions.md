# ADR-021: Continuous integration on GitHub Actions with path filters and images in GHCR

- Status: Proposed
- Date: 2026-11-17

## Context

The repository is public on GitHub. Standard GitHub-hosted runners are free for public repositories ([GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)); public repositories get 4-CPU Linux runners, and native `arm64` Linux runners are available ([GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)). The repository holds four code bases (Android, core, `delivery`, back office) and the API contract; most changes touch one of them. Third-party actions are a supply-chain risk (threat T25 in [security](../security.md)).

## Options

1. **GitHub Actions** with one workflow per purpose and path filters. Native to the repository, free, `arm64` runners for images.
2. **Another hosted CI** (GitLab CI, CircleCI). Extra account and integration, no benefit.
3. **No CI, local checks only.** Merges are not gated; regressions in the authorization matrix could slip through.

## Decision

GitHub Actions. Workflows and their triggers are described in [ci.md](../../ops/ci.md):

- `ci.yml` on pull requests and pushes to `main`: path-filtered jobs per code base; the contract lint and breaking-change check; the authorization matrix; secret scan. Required for merging.
- Emulator tests manually, by pull-request label or nightly when Android changed.
- Images for `amd64` and `arm64` built on native runners and published to `ghcr.io` on release tags.
- Deployment only by manual dispatch.

Hygiene: `permissions: {}` by default and per-job grants; third-party actions pinned by commit SHA; workflow linting; automated dependency update pull requests; artifacts kept 7 days.

Every job calls the same developer scripts that are used locally ([ADR-025](ADR-025-developer-commands-powershell-scripts.md)), so "green locally" means "green in CI".

## Consequences

- No CI cost while the repository is public; if it becomes private, the monthly included minutes apply and path filters keep usage low.
- Pinning actions by SHA requires update pull requests to bump them.
- Workflows must not contain repository-hygiene checks whose own text would violate repository rules; such checks run as local pre-commit hooks.

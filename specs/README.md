# Feature specifications

Each feature is specified before it is built. The process is described in [development workflow](../docs/ops/development-workflow.md); the order of features follows the [roadmap](../docs/roadmap/roadmap.md).

## Layout

```
specs/NNN-<feature>/
  spec.md                What and why: user scenarios, functional requirements, edge cases, success criteria
  checklists/            Quality checklists for the spec and, later, for requirements by area
  plan.md                How: modules, API and database changes, tests, risks, check against project principles
  research.md            Options considered for open technical questions (when needed)
  data-model.md          Entities and schema changes (when needed)
  contracts/             API contract changes for review (merged into api/openapi.yaml)
  quickstart.md          How to run and verify the feature by hand
  tasks.md               Ordered tasks, each 30–120 minutes, tests first; 👤 marks owner tasks
  device-runs.md         Device test matrix run reports (features touching background location)
```

`spec.md` is written and approved first; `plan.md` and the rest come only after approval.

## Status values

| Status | Meaning |
|---|---|
| Draft | Written, not yet reviewed |
| Approved | Approved by the owner; planning may start |
| In progress | Tasks are being implemented |
| Done | Definition of Done met, demo verified |

## Index

| Spec | Roadmap stage | Status |
|---|---|---|
| [001-walking-skeleton](001-walking-skeleton/spec.md) | 1 — Walking skeleton | Approved |
| [002-location-spike](002-location-spike/spec.md) | 2 — Location spike | Draft |

# Contract changes: `api/openapi.yaml`

**Feature**: [spec.md](../spec.md) · **Plan**: [plan.md](../plan.md)

Changes merged into [`api/openapi.yaml`](../../../api/openapi.yaml) by the first task of the core (contract first, ADR-009). No other operation changes.

## 1. `GET /v1/config` becomes public (FR-002, clarification of 2026-11-23)

Before:

```yaml
  /v1/config:
    get:
      tags: [Service]
      operationId: getRemoteConfig
      # inherits the global `security: [bearerAuth]`
      responses:
        '200': ...
        '304':
          description: Not modified.
        '401': { $ref: '#/components/responses/Unauthorized' }
```

After:

```yaml
  /v1/config:
    get:
      tags: [Service]
      operationId: getRemoteConfig
      summary: Remote configuration for the app and the location engine
      description: |
        Versioned configuration published by the operator: engine parameters,
        map style URL, limits and legal page URLs. Public: the app reads it
        before an account exists (for example for the legal links during
        onboarding). The app caches it and refetches when a report response
        announces a newer `configVersion`. Supports conditional requests with
        `If-None-Match`.
      security: []
      parameters:
        - name: If-None-Match
          in: header
          schema: { type: string }
      responses:
        '200':
          description: Current configuration.
          headers:
            ETag:
              schema: { type: string }
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RemoteConfig'
        '304':
          description: Not modified.
```

Compatibility: removing a security requirement and an error response does not break existing clients (none exist yet); the breaking-change check must pass (see [research R-09](../research.md#r-09-contract-checks-fr-017)).

## 2. `GET /health` description (FR-001)

The schema and status codes stay the same; the description states the clarified semantics:

```yaml
  /health:
    get:
      tags: [Service]
      operationId: getHealth
      summary: Readiness probe
      description: |
        `ok` (200) means the service is ready to serve requests: for the core,
        the database is reachable and all migrations are applied. Otherwise
        `down` (503). The body contains the status only.
      security: []
```

## 3. API guide

[`docs/api/README.md`](../../../docs/api/README.md) §3, row "Access token": "All `/v1` endpoints except `/v1/auth/*`, `/v1/config` (public) and `/v1/stream`".

## 4. Lint configuration

`api/redocly.yaml` is added with the `recommended` rule set. Any rule switched off or downgraded is listed with a one-line reason in that file (for example, `/health` has no `4xx` response by design).

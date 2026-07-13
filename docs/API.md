# Project Forge - API Reference (v1)

Base path: `/v1`. Full interactive OpenAPI docs are served by the running backend at
`/v1/swagger-ui.html` (spec JSON at `/v1/api-docs`) - this document is a quick
human-readable index, not the source of truth.

Authenticated requests: `Authorization: Bearer <accessToken>`.

## Tenancy

### `POST /v1/tenants` - public

Creates a tenant and its first Owner user, transactionally.

```json
// request
{
  "name": "Acme Corp",
  "slug": "acme",
  "ownerFullName": "Jane Owner",
  "ownerEmail": "jane@acme.example",
  "ownerPassword": "at-least-8-characters"
}
```

Response `201`: `{ "tenant": { "id", "name", "slug", "status", "createdAt" }, "ownerUserId", "ownerEmail" }`

## Auth

| Method | Path                  | Auth        | Notes                                             |
|--------|-----------------------|-------------|----------------------------------------------------|
| POST   | `/v1/auth/register`   | public      | Creates a MEMBER user under an existing tenant     |
| POST   | `/v1/auth/login`      | public      | `{tenantSlug, email, password}` -> access+refresh  |
| POST   | `/v1/auth/refresh`    | public      | `{refreshToken}` -> new access+refresh (rotated)   |
| POST   | `/v1/auth/logout`     | bearer      | `{refreshToken}` -> revokes it in Redis, 204        |

Login/refresh response:

```json
{ "accessToken": "...", "refreshToken": "...", "expiresInSeconds": 900, "tokenType": "Bearer" }
```

## Workflow definitions

All require a bearer token; the tenant is resolved from the token's `tenant_id` claim.

| Method | Path                              | Required permission            |
|--------|------------------------------------|----------------------------------|
| POST   | `/v1/workflows/definitions`        | `WORKFLOW_DEFINITION_CREATE`     |
| GET    | `/v1/workflows/definitions`        | `WORKFLOW_DEFINITION_READ`       |
| GET    | `/v1/workflows/definitions/{id}`   | `WORKFLOW_DEFINITION_READ`       |
| PUT    | `/v1/workflows/definitions/{id}`   | `WORKFLOW_DEFINITION_UPDATE`     |
| DELETE | `/v1/workflows/definitions/{id}`   | `WORKFLOW_DEFINITION_DELETE`     |

```json
// POST /v1/workflows/definitions
{
  "name": "Purchase Approval",
  "description": "optional",
  "states": ["DRAFT", "IN_REVIEW", "APPROVED", "REJECTED"],
  "transitions": [
    { "from": "DRAFT", "to": "IN_REVIEW" },
    { "from": "IN_REVIEW", "to": "APPROVED" },
    { "from": "IN_REVIEW", "to": "REJECTED" }
  ]
}
```

## Workflow instances

| Method | Path                                          | Required permission              |
|--------|-------------------------------------------------|-------------------------------------|
| POST   | `/v1/workflows/instances`                       | `WORKFLOW_INSTANCE_CREATE`          |
| GET    | `/v1/workflows/instances`                       | `WORKFLOW_INSTANCE_READ`            |
| GET    | `/v1/workflows/instances/{id}`                  | `WORKFLOW_INSTANCE_READ`            |
| POST   | `/v1/workflows/instances/{id}/transitions`      | `WORKFLOW_INSTANCE_TRANSITION`      |

`POST /v1/workflows/instances` body: `{ "workflowDefinitionId": "<uuid>" }` - the new
instance starts in the definition's first declared state.

`POST /v1/workflows/instances/{id}/transitions` body: `{ "toState": "IN_REVIEW" }` -
rejected `422` if that transition isn't declared on the definition.

## Errors

All errors share this shape:

```json
{
  "timestamp": "2026-07-13T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Workflow definition not found: <id>",
  "path": "/v1/workflows/definitions/<id>",
  "details": []
}
```

`401` = missing/invalid credentials. `403` = authenticated but lacking the required
permission. `422` = semantically invalid request (e.g. an undefined workflow
transition).

## Scaffolded, not implemented (return `501`)

`/v1/billing/**`, `/v1/notifications/**`, `/v1/dashboards/**`, `/v1/public/**`,
`/v1/sso/**` - see `docs/ARCHITECTURE.md` Section 6.

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
| POST   | `/v1/auth/register`   | public      | **Disabled by default** - returns `403`, see below |
| POST   | `/v1/auth/login`      | public      | `{tenantSlug, email, password}` -> access+refresh  |
| POST   | `/v1/auth/refresh`    | public      | `{refreshToken}` -> new access+refresh (rotated)   |
| POST   | `/v1/auth/logout`     | bearer      | `{refreshToken}` -> revokes it in Redis, 204        |

Login/refresh response:

```json
{ "accessToken": "...", "refreshToken": "...", "expiresInSeconds": 900, "tokenType": "Bearer" }
```

### Self-registration is off by default

`POST /v1/auth/register` returns `403` unless `forge.registration.self-service-enabled=true`
(env: `FORGE_SELF_SERVICE_REGISTRATION`). Knowing a tenant's slug must not be enough to
create an account inside that tenant. The sanctioned way to add a user is
`POST /v1/users` (permission `USER_INVITE`), which requires an authenticated administrator
of that tenant.

## Users

Tenant-scoped: the tenant comes from the caller's token, never the request. No endpoint
here accepts a tenant id, so cross-tenant user administration is impossible by
construction.

| Method | Path                          | Required permission |
|--------|-------------------------------|---------------------|
| POST   | `/v1/users`                   | `USER_INVITE`       |
| GET    | `/v1/users?page=0&size=20`    | `USER_READ`         |
| GET    | `/v1/users/{id}`              | `USER_READ`         |
| PATCH  | `/v1/users/{id}`              | `USER_UPDATE`       |
| POST   | `/v1/users/{id}/deactivate`   | `USER_DEACTIVATE`   |
| POST   | `/v1/users/{id}/activate`     | `USER_UPDATE`       |

```json
// POST /v1/users
{
  "fullName": "Sam Member",
  "email": "sam@acme.example",
  "initialPassword": "at-least-8-characters",
  "roleName": "MEMBER"
}
```

`PATCH /v1/users/{id}` accepts `{ "fullName": "...", "roleName": "..." }`; both optional, a
null field is left unchanged. Email is not editable here - it is the login identity and needs
a verification flow.

Guards that return `409`: deactivating your own account, and deactivating or demoting the
last active `OWNER` (which would leave the organization unadministerable).

Note: deactivating a user does not invalidate their outstanding access token, which remains
valid until it expires (15 min default). Refresh is blocked immediately, so the session
cannot be extended past that window.

## Roles and permissions

| Method | Path                  | Required permission |
|--------|-----------------------|---------------------|
| GET    | `/v1/roles`           | `USER_READ`         |
| GET    | `/v1/roles/{id}`      | `USER_READ`         |
| POST   | `/v1/roles`           | `ROLE_MANAGE`       |
| PUT    | `/v1/roles/{id}`      | `ROLE_MANAGE`       |
| DELETE | `/v1/roles/{id}`      | `ROLE_MANAGE`       |
| GET    | `/v1/permissions`     | `USER_READ`         |

```json
// POST /v1/roles
{ "name": "AUDITOR", "permissions": ["AUDIT_LOG_READ", "USER_READ"] }
```

Reads use `USER_READ` because assigning a role requires being able to list roles. Two rules
are enforced server-side:

- **System roles are immutable.** `OWNER`/`ADMIN`/`MEMBER` cannot be renamed, re-granted or
  deleted (`409`), and their names are reserved case-insensitively.
- **No privilege escalation.** You may only grant permissions you hold yourself; attempting
  otherwise returns `403` naming the offending permissions.

Deleting a role still assigned to users returns `409`. An unknown permission name is a `400`.

## Tenant administration

Addressed as `/current` - there is deliberately no `/{id}` form.

| Method | Path                              | Required permission |
|--------|-----------------------------------|---------------------|
| GET    | `/v1/tenants/current`             | `TENANT_READ`       |
| PATCH  | `/v1/tenants/current`             | `TENANT_UPDATE`     |
| POST   | `/v1/tenants/current/suspend`     | `TENANT_SUSPEND`    |
| POST   | `/v1/tenants/current/activate`    | `TENANT_SUSPEND`    |

`PATCH` accepts `{ "name": "New Display Name" }`. Only the display name is mutable: the slug
is part of every user's login identity, so changing it is a migration rather than an edit.

Suspending blocks all logins and refreshes for the tenant. Note that suspending your own
tenant leaves nobody able to log in and undo it - recovery needs operator database access.

## Audit log

| Method | Path                              | Required permission |
|--------|-----------------------------------|---------------------|
| GET    | `/v1/audit-logs?page=0&size=50`   | `AUDIT_LOG_READ`    |

Newest first, tenant-scoped, read-only - there is no write, update or delete endpoint. Entries
are written only by `AuditAspect` on `@Audited` service methods.

## Pagination

List endpoints that can grow unbounded return this envelope rather than a bare array:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`page` defaults to 0. `size` defaults to 20 (50 for audit logs) and is clamped to a maximum
(100, or 200 for audit logs) so an oversized `size` cannot be used to force an unbounded query.

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

| Status | Meaning                                                                             |
|--------|-------------------------------------------------------------------------------------|
| `400`  | Malformed or invalid request payload (including an unknown permission name)          |
| `401`  | Missing or invalid credentials                                                       |
| `403`  | Authenticated but lacking the required permission, or the operation is disabled      |
| `404`  | Not found - also returned when the row exists but belongs to another tenant          |
| `409`  | Conflict: duplicate, protected invariant (system role, last owner), or a lost update |
| `422`  | Semantically invalid request (e.g. an undefined workflow transition)                 |
| `500`  | Internal error. The response body never contains internal detail; it is logged       |

A `404` rather than `403` for another tenant's row is deliberate: confirming that an id exists
in a tenant you cannot see is itself a leak.

`409` on `POST /v1/workflows/instances/{id}/transitions` means two callers transitioned the
same instance concurrently and this one lost the optimistic lock - re-read the instance and
retry against its new state.

## Scheduled maintenance

Not endpoints, but they affect what the API reports:

- **`WORKFLOW_INSTANCES_PER_MONTH` resets at 00:05 UTC on the 1st** of each calendar month, so the
  `used` value in `GET /v1/billing/subscription` drops to zero then. It is a calendar month, not a
  per-tenant billing anniversary.
- Expired SSO login states are swept hourly; they are already unusable the moment they expire, so
  this does not change any response.

Both run under a Redis lock so replicas do not double-run them (`docs/ARCHITECTURE.md` 6a).

## Operational endpoints

| Method | Path                 | Auth   | Notes                                              |
|--------|----------------------|--------|----------------------------------------------------|
| GET    | `/actuator/health`   | public | `{"status":"UP"}` - used as the compose healthcheck |
| GET    | `/actuator/info`     | public | Build info                                          |

Only `health` and `info` are exposed (`management.endpoints.web.exposure.include`). Everything
else actuator can serve stays off.

## Notifications

Personal inbox - no permission gates it, because the recipient is always the caller (taken from
the token, never a parameter). There is deliberately no way to read anyone else's.

| Method | Path                                | Notes                                  |
|--------|-------------------------------------|----------------------------------------|
| GET    | `/v1/notifications?page=0&size=20`  | Newest first, paginated envelope       |
| GET    | `/v1/notifications/unread-count`    | `{"unread": 3}` - cheap enough to poll |
| POST   | `/v1/notifications/{id}/read`       | Idempotent; keeps the original time    |
| POST   | `/v1/notifications/read-all`        | `{"markedRead": 7}`                    |

## Billing

| Method | Path                              | Required permission |
|--------|-----------------------------------|---------------------|
| GET    | `/v1/billing/subscription`        | `TENANT_READ`       |
| POST   | `/v1/billing/subscription/plan`   | `TENANT_UPDATE`     |

Plans: `FREE` (3 seats / 2 workflows / 100 instances-month / 0 API keys), `STARTER` (10/10/1k/2),
`PROFESSIONAL` (50/100/25k/10), `ENTERPRISE` (unlimited).

Exceeding a limit returns **`402 Payment Required`**, not 403 - a plan limit is the organization's
to change, whereas 403 would imply the caller personally lacks authority. A `PAST_DUE` or
`CANCELED` subscription blocks writes while leaving all data readable.

`POST /v1/billing/subscription/plan` requires `forge.billing.self-serve-plan-change=true` and
refuses to downgrade below current usage (`409`), which would otherwise strand a tenant over-limit
with no way back.

### `POST /v1/webhooks/billing` - public, HMAC-authenticated

Send `X-Forge-Signature`: hex HMAC-SHA256 of the **raw request body** using
`forge.billing.webhook-secret`. Body shape:

```json
{ "id": "evt_123", "type": "subscription.updated",
  "data": { "subscription_id": "sub_abc", "plan": "PROFESSIONAL", "status": "ACTIVE",
            "current_period_end": 1735689600, "cancel_at_period_end": false } }
```

Types: `subscription.created|updated|canceled`, `payment.succeeded|failed`.

Response codes are chosen for provider retry behaviour, not for humans: duplicates, unknown
subscriptions and unhandled types all return `200` (final outcomes - retrying cannot help). Only a
bad signature (`401`) and a missing secret (`503`) are non-2xx. Events are deduplicated by
`(provider, id)`, so at-least-once delivery is safe.

## API keys

Management, on the authenticated API:

| Method | Path                  | Required permission |
|--------|-----------------------|---------------------|
| POST   | `/v1/api-keys`        | `ROLE_MANAGE`       |
| GET    | `/v1/api-keys`        | `ROLE_MANAGE`       |
| DELETE | `/v1/api-keys/{id}`   | `ROLE_MANAGE`       |

```json
// POST /v1/api-keys
{ "name": "CI pipeline", "scopes": ["INSTANCES_READ"], "expiresAt": null }
```

The response carries `secret` - **the only time it exists**. It is stored as a peppered HMAC and
cannot be retrieved again; lose it and you revoke and reissue. `DELETE` revokes (records a
timestamp) rather than deleting, so the audit trail survives.

## Public API

Authenticate with `X-Api-Key: forge_<keyId>_<secret>`. No bearer token is accepted here, and an API
key is not accepted anywhere else - scopes are `SCOPE_`-prefixed authorities that no user role can
hold, and the filter only runs on this prefix.

| Method | Path                          | Required scope    |
|--------|-------------------------------|-------------------|
| GET    | `/v1/public/ping`             | any valid key     |
| GET    | `/v1/public/workflows`        | `WORKFLOWS_READ`  |
| GET    | `/v1/public/instances`        | `INSTANCES_READ`  |
| GET    | `/v1/public/instances/{id}`   | `INSTANCES_READ`  |

Rate limited per key (`forge.api-keys.rate-limit-per-minute`, default 120); exceeding it returns
`429` with `Retry-After`. Deliberately narrow and read-only - the authenticated surface is not
re-exposed here.

## Dashboards

| Method | Path                    | Required permission                    |
|--------|-------------------------|----------------------------------------|
| GET    | `/v1/dashboards`        | authenticated                          |
| GET    | `/v1/dashboards/{id}`   | authenticated (own or shared)          |
| POST   | `/v1/dashboards`        | `TENANT_UPDATE` only if `shared: true` |
| PUT    | `/v1/dashboards/{id}`   | `TENANT_UPDATE` only if `shared: true` |
| DELETE | `/v1/dashboards/{id}`   | authenticated (own or shared)          |

Widget types: `WORKFLOW_DEFINITION_COUNT`, `WORKFLOW_INSTANCE_COUNT`, `INSTANCES_BY_STATE`,
`ACTIVE_USER_COUNT`, `RECENT_AUDIT_ACTIVITY`, `SUBSCRIPTION_USAGE`. Widget `data` is computed on
read (never cached, never persisted) through the same tenant-scoped repositories as everything
else. `PUT` replaces the widget set wholesale; max 24 per dashboard.

## SSO (OIDC)

Administration - authenticated, `TENANT_UPDATE`:

| Method | Path                          |
|--------|-------------------------------|
| GET    | `/v1/tenants/current/sso`     |
| PUT    | `/v1/tenants/current/sso`     |
| DELETE | `/v1/tenants/current/sso`     |

`clientSecret` is write-only: send it to set or rotate, omit it to leave unchanged. Responses
return `clientSecretSet: true/false` and never the value. The response also gives you the
`loginUrl` and `callbackUrl` to register at your IdP.

Login - public, per tenant:

| Method   | Path                              | Notes                                   |
|----------|-----------------------------------|-----------------------------------------|
| GET      | `/v1/sso/{tenantSlug}/login`      | `302` to the IdP with `state` + `nonce`  |
| GET/POST | `/v1/sso/{tenantSlug}/callback`   | Returns the same token pair as password login |

`state` is single-use and bound to its tenant, so a captured callback cannot be replayed or
redeemed against another organization. Tokens are returned as JSON, never in a redirect URL - a
refresh token in a query string ends up in browser history, access logs and `Referer` headers.

`autoProvision: true` creates accounts on first successful IdP login; `false` means SSO only
authenticates users an administrator already invited. Group claims map to roles via
`roleMappingJson` (e.g. `{"IT-Admins-EMEA": "ADMIN"}`); on multiple matches the most privileged
wins.

**SAML** can be stored but not enabled - `PUT` with `protocol: SAML, enabled: true` returns `409`
stating so rather than silently doing nothing.

# Project Forge

Multi-tenant Enterprise SaaS Platform - foundation repository. Real, working source
code the team extends; not a mockup. See `docs/ARCHITECTURE.md` for the full design,
`docs/API.md` for the REST surface, and `docs/TEAM.md` for the role table.

## What's implemented (real, working code)

- **Multi-tenancy**: `Tenant` entity, `POST /v1/tenants` (creates tenant + first Owner
  user transactionally), request-scoped `TenantContext` resolved from the JWT tenant
  claim (or `X-Tenant-Id` header pre-auth), enforced at the repository layer via a
  custom base repository that disables tenant-unaware lookups at runtime, reinforced
  by a Hibernate `@Filter`.
- **Auth**: register / login / refresh / logout. JWT access tokens (15 min default,
  tenant_id + role + permissions claims), opaque rotating refresh tokens stored in
  Redis (revocable - see `RefreshTokenService`), bcrypt (default) / Argon2 password
  hashing.
- **RBAC**: `Permission` enum catalog, `Role` entity, OWNER/ADMIN/MEMBER system roles
  seeded on tenant creation, enforced via real Spring Security `@PreAuthorize` on
  every write endpoint.
- **Workflows** (the tenant-isolated resource module): `WorkflowDefinition`
  (states/transitions as JSON) + `WorkflowInstance` (current state + append-only
  history), full CRUD, transition validation, every query tenant-filtered. See
  `backend/src/test/java/com/zentrox/forge/tenancy/TenantIsolationTest.java` for the
  test proving tenant A cannot read tenant B's workflows.
- **Audit log**: append-only, written via a Spring AOP aspect (`@Audited` annotation)
  on service write methods.
- **User & role administration**: `POST /v1/users` (invite), paginated list, update, activate /
  deactivate; custom per-tenant roles with two server-enforced invariants - system roles are
  immutable, and nobody can grant a permission they don't hold themselves. `GET /v1/permissions`
  exposes the catalog so a UI doesn't have to hardcode a copy of the enum.
- **Tenant administration**: `GET`/`PATCH /v1/tenants/current`, suspend / activate. Addressed as
  `/current`, never `/{id}`, so cross-tenant administration is impossible by construction.
- **Audit log read API**: `GET /v1/audit-logs`, paginated, newest first, tenant-scoped, read-only.
  Audit rows were being written from the first commit but could never be read back.
- **Scheduled maintenance**: monthly reset of the metered instance counter and a sweep of expired
  SSO login states, both under a Redis lock so replicas do not double-run them. See
  `docs/ARCHITECTURE.md` 6a.
- **Frontend**: React + TypeScript + Tailwind + Zustand SPA covering every implemented module -
  Dashboards (widget data resolved server-side), Workflows, Inbox (with an unread badge in the nav),
  People, Roles (driven by `GET /v1/permissions`, not a hardcoded enum copy), Plan & usage (usage
  bars that colour as a limit approaches), API keys (secret surfaced once in a panel that must be
  dismissed deliberately), Audit log, and Settings. Automatic access-token refresh on 401 with
  concurrent refreshes coalesced into one request. Server-side error messages are surfaced rather
  than replaced with a generic string - for 402/403/409 that message *is* the explanation ("Your
  FREE plan allows 3 seats and you are using 3", "you cannot grant permissions you do not hold
  yourself"). Nav links are never hidden by permission: the server is the authority, and hiding a
  link secures nothing while making a missing feature look absent rather than unauthorized.

- **Notifications** (FRD-10): in-app inbox (`GET /v1/notifications`, unread count, mark read /
  read-all) plus an email channel behind an `EmailSender` abstraction. Delivery is driven by domain
  events published with `ApplicationEventPublisher` and consumed by an `@Async`
  `@TransactionalEventListener(AFTER_COMMIT)` - so business logic never calls the notifier directly,
  and nothing is announced about a transaction that rolled back.
- **Billing & entitlements** (FRD-9): plan catalog with per-metric limits, one subscription per
  tenant, and real enforcement via a `@RequiresEntitlement` aspect that returns **402 Payment
  Required**. Population metrics (seats, definitions, API keys) are counted live; rate metrics
  (instances/month) are metered. Webhook receiver is HMAC-signature-verified and idempotent.
- **API keys & public API** (FRD-12): `forge_<keyId>_<secret>` keys stored only as a peppered HMAC
  and shown exactly once, with scopes, expiry and revocation. `ApiKeyAuthenticationFilter`
  authenticates `/v1/public/**` only, using a non-user principal whose `SCOPE_` authorities cannot
  satisfy any user-facing permission check. Redis fixed-window rate limiting per key.
- **Configurable dashboards** (FRD-11): dashboards and widgets, private or shared per tenant. Widget
  data is resolved on read through the existing tenant-scoped repositories - there is deliberately
  no parallel query path to audit.
- **SSO** (FRD-13): per-tenant OIDC authorization-code flow with server-side `state` (single-use)
  and `nonce`, terminating in the same token issuance as password login. IdP group claims map to
  Forge roles via configurable per-tenant mapping; IdP client secrets are AES-GCM encrypted at rest
  and never returned by any endpoint.

## Provider integration: what you supply

Everything above runs with no third-party credentials, because providers sit behind abstractions
with working defaults. To go live you supply configuration, not code:

| Concern | Default behaviour | To go live |
|---|---|---|
| Email | `LoggingEmailSender` logs the message at WARN | `forge.mail.enabled=true` + `spring.mail.*` (works with SES/SendGrid/Postmark/any SMTP) |
| Payments | `forge.billing.provider=manual`, self-serve plan changes allowed | Set `forge.billing.webhook-secret`, point your provider's webhook at `POST /v1/webhooks/billing`, and set `self-serve-plan-change=false` so the provider owns the plan |
| SSO | No connection configured | Per tenant: `PUT /v1/tenants/current/sso` with your IdP's issuer, client id/secret and endpoints |

**SAML is the one thing not implemented.** The protocol value, schema columns and API accept and
store a SAML connection, but enabling one returns `409` naming the limitation rather than
half-working. OIDC covers Entra ID, Okta, Auth0 and Google Workspace.

## Remaining scaffolding

None. All five previously-scaffolded packages are implemented; no controller returns
`501 Not Implemented` any more.

## Verification status

The foundation now compiles and its test suite passes. Verified with `maven:3.9-eclipse-temurin-17`
and Node 22:

```
cd backend && mvn -B verify          # 134 tests, 0 failures (133 + the Docker-gated migration test)
cd ../frontend && npm ci && npm run build && npm run lint
```

### History: the four defects that had to be fixed first

The original commit was written in a sandbox with no Java, Maven or Node toolchain, and had
therefore never been compiled. It did not build. For the record, and because the second of these
was a design error rather than a typo:

1. `WorkflowService` captured a reassigned local in a lambda - *"local variables referenced from a
   lambda expression must be final or effectively final"*.
2. `TenantScopedRepositoryImpl` declared `implements TenantScopedRepository`. The methods on that
   interface are Spring Data **derived queries**, generated by the repository proxy at runtime; a
   concrete base class cannot implement them, and claiming the interface made `javac` demand method
   bodies that must not exist.
3. All five tenant entities declared the same `@FilterDef(name = "tenantFilter")`. Hibernate allows
   exactly one definition per filter name, so **the application context could not start at all** -
   invisible until the first two were fixed. The definition now lives once, in
   `entity/package-info.java`.
4. `@types/node` was missing from the frontend's devDependencies, so `vite.config.ts` could not be
   type-checked.

CI (`.github/workflows/ci.yml`) also failed before installing anything, because it pointed
`setup-node`'s cache at a `package-lock.json` that was never committed. The lockfile is committed
now and both CI and the Docker build use `npm ci`.

### What the tests actually cover

| Suite | Proves |
|---|---|
| `TenantIsolationTest` (7) | Tenant A cannot read tenant B's workflows, at the repository *and* service layer; the tenant-unaware `findById` is structurally disabled |
| `UserManagementServiceTest` (10) | The same, for the user-administration module; plus password hashing, per-tenant email uniqueness, and the last-owner guard |
| `RoleManagementServiceTest` (8) | System roles are immutable; no caller can grant a permission they don't hold |
| `AdminEndpointRbacTest` (13) | Every new admin endpoint rejects callers lacking its permission, through the real filter chain; an audited write is readable back from the audit log |
| `RbacEnforcementTest` (3) | `@PreAuthorize` on workflow writes; 401 vs 403 |
| `JwtServiceTest` (2) | Claim round-trip; tampered tokens rejected |
| `NotificationServiceTest` (8) | An inbox is per-user as well as per-tenant: nobody reads another user's notifications, and bulk mark-read touches only the caller's rows |
| `BillingWebhookServiceTest` (13) | Forged and tampered signatures rejected (constant-time); replayed events applied once; unknown plan names change nothing |
| `EntitlementEnforcementTest` (10) | Limits actually gate writes (402); deactivated users free their seat; past-due blocks writes while data stays readable |
| `ApiKeyAuthenticationTest` (15) | Secret never stored or re-shown; revoked/expired keys fail; a key cannot reach the user API and a user JWT cannot satisfy a scope |
| `DashboardServiceTest` (12) | Private vs shared visibility, no cross-tenant leakage, every widget type resolves |
| `SsoTest` (18) | Secrets encrypted with a fresh IV and never returned; forged state rejected; group→role mapping with most-privileged-wins |
| `MaintenanceJobsTest` (6) | Usage reset is idempotent, keeps the row so the next increment stays atomic, and leaves other metrics alone; the sweep deletes only expired states |
| `FlywayMigrationParityTest` (1) | Real Postgres + real Flyway + `ddl-auto: validate` - catches entity/migration drift |

That last one exists because the fast suite runs H2 with `ddl-auto: create-drop` and Flyway
**disabled**, so it never executes `db/migration`. Production does the opposite. Without this test
a missing migration column is invisible in CI and fatal at deploy.

It needs a reachable Docker daemon, which GitHub's Linux runners have. It does *not* work when
Maven itself runs inside a container against Docker Desktop for Windows - the shared
`/var/run/docker.sock` is a named-pipe proxy that answers `/info` with HTTP 400. In that situation
run the build on the host, or verify the same property with `docker compose up --build`: the
application boots with Flyway on and `ddl-auto: validate`, so a clean startup proves the same
parity (the log shows *"Successfully applied 2 migrations"* followed by
*"Started ForgeApplication"*).

## Running locally

```
cp .env.example .env   # fill in FORGE_JWT_SECRET and passwords
docker compose up --build
```

- Backend: http://localhost:8080 (OpenAPI UI at `/v1/swagger-ui.html`, health at `/actuator/health`)
- Frontend: http://localhost:5173

Only the **host** side of each port is configurable, via `FORGE_BACKEND_PORT`,
`FORGE_FRONTEND_PORT`, `FORGE_POSTGRES_PORT` and `FORGE_REDIS_PORT` in `.env` - useful when
something on your machine already owns 8080 or 5432. Container ports and service names are fixed,
so the SPA still reaches the API at `http://backend:8080` through nginx regardless of what you
set.

Or run each service individually against `docker compose up postgres redis`:

```
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

## Repository layout

```
backend/    Spring Boot 3 / Java 17 API - see backend/pom.xml, src/main, src/test
frontend/   React + TypeScript + Tailwind + Zustand SPA - see frontend/package.json
docs/       ARCHITECTURE.md, API.md, TEAM.md
.github/    CI workflow (backend test, frontend build, docker image build)
docker-compose.yml, .env.example
```

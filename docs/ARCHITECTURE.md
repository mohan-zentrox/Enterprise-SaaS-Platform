# Project Forge - Architecture

## 1. Overview

Project Forge is a multi-tenant Enterprise SaaS Platform. This repository is the
foundation the team extends: tenancy, auth, RBAC, user/role/tenant administration, workflows,
audit logging, notifications, billing with entitlement enforcement, API keys with a public API,
configurable dashboards, and per-tenant OIDC single sign-on.

```
                        ┌────────────────────┐
                        │   React + TS SPA    │
                        │ (frontend/)          │
                        └──────────┬───────────┘
                                   │ REST /v1, JWT bearer
                        ┌──────────▼───────────┐
                        │  Spring Boot API      │
                        │  (backend/)           │
                        │                        │
   JwtAuthenticationFilter → TenantFilterInterceptor → @PreAuthorize → Controller
                        │        │        │
                        │        │        └─ AuditAspect (AOP, @Audited methods)
                        │        └─ TenantContext (ThreadLocal) + Hibernate @Filter
                        │
                 ┌──────▼───────┐        ┌────────────┐
                 │  PostgreSQL   │        │   Redis     │
                 │  (Flyway)     │        │ refresh     │
                 │  tenant_id on │        │ tokens      │
                 │  every table  │        └────────────┘
                 └───────────────┘
```

## 2. Multi-tenancy

Every tenant-owned table carries a `tenant_id` column (see
`backend/src/main/resources/db/migration/V1__init_schema.sql`). Isolation is enforced
at three layers, deliberately redundant:

1. **Repository layer (structural, primary):** tenant-scoped entities extend
   `entity.TenantScopedEntity`; their repositories extend
   `repository.tenant.TenantScopedRepository`, whose base implementation
   (`TenantScopedRepositoryImpl`) *disables* the inherited tenant-unaware
   `findById`/`findAll`/`deleteById` at runtime - they throw `UnsupportedOperationException`
   instead of silently returning cross-tenant data. Every real lookup goes through
   `findByIdAndTenantId` / `findAllByTenantId` and friends.
2. **Hibernate `@Filter` (defense in depth):** each tenant-scoped entity declares a
   `tenantFilter` (`tenant_id = :tenantId`), enabled per-request by
   `tenancy.TenantFilterInterceptor`. Catches a raw JPQL/criteria query written by a
   future contributor that bypasses the repository layer.
3. **`TenantContext` (request-scoped binding):** a `ThreadLocal` populated by
   `TenantFilterInterceptor` from the authenticated principal's `tenant_id` JWT claim
   (or an `X-Tenant-Id` header for pre-auth/service-to-service cases), read by every
   tenant-scoped service method, cleared at the end of every request.

`POST /v1/tenants` creates a `Tenant`, its three system roles, and its first Owner
user in one `@Transactional` method (`TenantService#createTenant`).

See `backend/src/test/java/com/zentrox/forge/tenancy/TenantIsolationTest.java` for the
test proving tenant A cannot read tenant B's workflow definitions at either layer.

## 3. AuthN / AuthZ

- **Access tokens:** short-lived JWT (HS256), claims: `sub` (user id), `tenant_id`,
  `role`, `permissions` (embedded so authorization is stateless - no DB hit per
  request). See `security.JwtService`.
- **Refresh tokens:** opaque random strings, never JWTs, stored in Redis
  (`forge:refresh:<token> -> "<tenantId>:<userId>"`, TTL-bound). Rotated on every use
  (`security.RefreshTokenService` / old token deleted, new one issued), revoked
  outright on logout. This is what makes server-side revocation real - possession of
  the opaque string means nothing once its Redis key is gone.
- **RBAC:** `Permission` enum catalog, `Role` entity (per-tenant, holds a
  `Set<Permission>`), three system roles (OWNER/ADMIN/MEMBER) seeded on tenant
  creation with grants defined in `service.RoleCatalog`. Enforcement is Spring
  Security's real `@PreAuthorize("hasAuthority('...')")` on every write controller
  method - not a custom reimplementation.
- **Password hashing:** bcrypt (cost 12) by default via a `DelegatingPasswordEncoder`;
  Argon2 is registered and selectable per-hash without a migration.

## 4. Workflows (the concrete tenant-isolated resource module)

`WorkflowDefinition` (states/transitions as JSON text - portable between H2-in-tests
and Postgres-in-prod) and `WorkflowInstance` (current state + append-only JSON
transition history). Full CRUD + a `POST .../transitions` action that validates the
requested transition against the definition before applying it. See
`service.WorkflowService`.

## 5. Audit log

`AuditLog` is append-only: written exclusively by `aop.AuditAspect`, an
`@AfterReturning` advice on any service method annotated `@Audited(action=..,
entityType=..)`. Nothing else writes to it, and nothing updates or deletes a row.
Failure to write an audit entry never fails the underlying business operation (logged
and swallowed) - audit logging is secondary to the write itself.

## 6. Feature modules

All five formerly-scaffolded packages are implemented. Each keeps to one rule: **no module invents a
new data-access path.** Everything reads through the tenant-scoped repositories described in Section
2, so tenant isolation is proved once rather than re-argued per feature.

### `notification/` - FRD-10

In-app inbox plus email behind an `EmailSender` interface (`LoggingEmailSender` by default,
`SmtpEmailSender` when `forge.mail.enabled=true`). Delivery is event-driven: services publish
`UserInvitedEvent` / `WorkflowTransitionedEvent`, and `NotificationEventListener` consumes them
`@Async` on `@TransactionalEventListener(AFTER_COMMIT)`.

Two consequences worth knowing. Nothing is announced about a transaction that rolled back. And
because the listener runs on another thread, `TenantContext` (a ThreadLocal) is empty there - which
is why every event carries its `tenantId` and why `NotificationService`'s write methods take it as a
parameter instead of reading the context.

An inbox is per-user, not just per-tenant: the recipient always comes from the token, so there is no
permission that could grant access to someone else's notifications.

### `billing/` - FRD-9

Plan limits live on the `SubscriptionPlan` enum, not in a table: they are product definitions that
ship with a release, and putting them in the database invites per-tenant edits no code validates.

Enforcement is a `@RequiresEntitlement` aspect mirroring `AuditAspect` - `@Before` rejects an
over-limit call (402) before any work happens, `@AfterReturning` meters only successful calls. It
orders ahead of the transaction advice so a rejected call never opens a transaction. Unlike the audit
aspect, failures here are **not** swallowed: a swallowed audit write loses a record, a swallowed
entitlement check gives away unpaid capacity.

Metrics are of two kinds, deliberately handled differently. Populations (seats, definitions, API
keys) are counted live from the tables that already hold them - a parallel counter would drift the
first time something was deleted without decrementing. Rates (instances/month) have nothing to count
and so are metered in `tenant_usage` with an atomic SQL increment.

The webhook receiver is the only unauthenticated write path into billing state, and has three
defences: constant-time HMAC over the raw bytes, a mandatory secret (refuse rather than trust), and
idempotency by `(provider, event_id)`. The claim lives in `WebhookEventLedger` with
`REQUIRES_NEW` - a constraint violation dooms its transaction, so the duplicate has to be caught
*outside* that transaction boundary or the commit fails anyway.

### `publicapi/` - FRD-12

Keys are `forge_<keyId>_<secret>`: the id makes verification one indexed lookup, the secret is 32
random bytes stored only as a peppered HMAC. HMAC rather than bcrypt because this is verified on
every request and the secret is high-entropy - bcrypt's deliberate slowness protects low-entropy
human passwords and buys nothing here.

`ApiKeyAuthenticationFilter` is restricted to `/v1/public/**` by `shouldNotFilter`, and rejects
with 401 itself rather than passing through (the prefix is `permitAll`, so there is no later gate).
Authorities are `SCOPE_`-prefixed so an API key can never satisfy a user permission check and a user
JWT can never satisfy a scope check - the two credential types are disjoint by construction.

Rate limiting is a Redis fixed window, and **fails open**: rate limiting guards against load, not
access, and a Redis outage taking down every customer integration would do more damage than the
excess traffic.

### `dashboard/` - FRD-11

Dashboards are private (owner set) or shared (owner null). Widget data is resolved on read by
`WidgetDataProvider`, always through the existing tenant-scoped repositories. This is the module
where hand-written aggregate SQL is most tempting and most dangerous - every such query is a fresh
chance to omit the tenant predicate.

Note `DashboardRepository.findVisibleTo` is an explicit `@Query`: the derived-method equivalent
relies on Spring Data's `And`-over-`Or` precedence with no parentheses available, where one slip
produces `(tenant = ? AND owner = ?) OR (owner IS NULL)` and leaks every tenant's shared dashboards.

### `sso/` - FRD-13

Per-tenant OIDC authorization-code flow, terminating in `AuthService#issueTokensForSsoLogin` - the
same issuance as password login, so nothing downstream needs to know how a user authenticated.

`state` is server-side, single-use and tenant-bound (login CSRF, replay, and cross-tenant
redemption); `nonce` binds the id_token to the request. The redirect URI comes from
`forge.sso.base-url`, never from the request, because behind a proxy `getRequestURL()` reports the
internal host and `Host` is client-supplied.

IdP client secrets are the one piece of recoverable secret material in the system - they must be
replayed to the token endpoint - so they are AES-GCM encrypted with a fresh random IV per write,
under a key separate from every other secret.

Two limitations stated rather than hidden. The id_token signature is not verified against JWKS: it
is fetched by us over TLS from the token endpoint using our client credentials, so it is not
attacker-supplied - but this becomes mandatory if an implicit or hybrid flow is ever added.
`jwksUri` is already captured for that. And SAML is storable but not enableable; enabling returns
409.

## 6a. Scheduled maintenance

`scheduling.MaintenanceJobs` holds the periodic work the feature modules require:

- **Monthly usage reset.** `WORKFLOW_INSTANCES_PER_MONTH` is a rate, so without a reset the counter
  climbs forever and every tenant eventually hits their limit permanently. Zeroes the rows rather
  than deleting them, because the row's existence is what keeps `recordUsage` a single atomic
  `UPDATE` instead of racing to insert.
- **Expired SSO login-state sweep.** Every abandoned login leaves a row; these are already unusable
  once expired, so this is disk hygiene rather than a security control.

Both run under `scheduling.SchedulerLock`, a Redis `SET NX EX` lock, because `@Scheduled` fires on
*every* application instance - two replicas would otherwise run the reset twice. The lock **fails
closed** (no Redis, no job), which is the opposite of the rate limiter's fail-open: skipping one
maintenance tick costs nothing, whereas an unsynchronised reset corrupts counters. It is safe here
only because both jobs are idempotent, and the class says so - it must not be reused for a job where
a duplicate run would be harmful.

`@EnableScheduling` is on `ForgeApplication`; without it these methods are silently never invoked.
The test profile pins the schedules far into the future so no background job mutates state
underneath an assertion.

## 7. Cross-cutting infra

- **Redis:** refresh tokens today; rate-limiting counters and the notification queue
  are natural next uses of the same connection.
- **Flyway:** all schema change goes through `db/migration/V<n>__description.sql`,
  applied on startup (`spring.flyway.enabled=true`), never `ddl-auto: update` in any
  real environment (`validate` only).
- **OpenAPI:** live at `/v1/api-docs` and `/v1/swagger-ui.html` (springdoc).

## 8. Hardening backlog (known gaps, intentionally deferred)

- No DB-level trigger preventing UPDATE/DELETE on `audit_logs` (currently
  enforced only by "nothing in the codebase calls it").
- No rate limiting yet (Redis is wired for it - see `publicapi` TODOs).
- No password reset / email verification flow. Consequently `POST /v1/users` takes an
  `initialPassword` chosen by the inviting administrator instead of sending an invitation token,
  and the invited user is not forced to change it on first login. Both need the email channel in
  the `notification/` scaffold.
- `@AfterThrowing` audit entries (failed attempts) are not recorded, only successes. Failed
  logins in particular are the entries a security reviewer asks for first.
- **No immediate session revocation.** Deactivating a user, demoting them, or suspending a tenant
  takes effect on the next refresh, but their current access token stays valid until it expires
  (15 min default) because authorization is deliberately stateless - the permission set is a JWT
  claim and nothing is re-read per request. Closing this properly means a per-user token
  generation counter in Redis, checked by `JwtAuthenticationFilter`; that trades one Redis read
  per request for instant revocation, which is a decision to make explicitly rather than drift
  into.
- Refresh tokens are persisted in `localStorage` by the SPA (see `frontend/src/store/authStore.ts`),
  so an XSS would exfiltrate a 30-day credential. An httpOnly, `SameSite=Strict` cookie is the
  fix; the rotation logic in `RefreshTokenService` does not change.
- `GET /v1/workflows/definitions` and `/instances` still return unbounded arrays. The newer list
  endpoints (`/v1/users`, `/v1/audit-logs`) use the `PageResponse` envelope; the workflow
  endpoints should be migrated to it, which is a coordinated change with the SPA's
  `api/workflows.ts`.

### Added by the feature modules

- No durable retry for email. A send that fails is logged and dropped; an outbox table is the fix.
- The OIDC id_token signature is not verified against JWKS (sound for this flow, mandatory before
  adding implicit/hybrid - see Section 6).
- SAML is storable but not implemented.
- Rate limiting is a fixed window, so a burst of up to 2x the limit can cross a window boundary.
- Usage resets on the calendar month, not on each tenant's billing anniversary. Per-tenant periods
  need the counter keyed by period, which is a schema change worth making once real invoicing exists.
- No UI for SSO configuration - it is the one administration surface still API-only.

### Closed since the foundation commit

- Custom per-tenant roles now exist (`RoleManagementService`), with system roles immutable and
  privilege escalation blocked.
- The ten orphaned permissions (`USER_*`, `ROLE_MANAGE`, `TENANT_*`, `AUDIT_LOG_READ`) now have
  endpoints; previously the catalog described authority the API could not express.
- CORS reads `forge.cors.allowed-origins` instead of allowing every origin with credentials.
- Public self-registration is off by default.
- Workflow instance transitions are protected by an optimistic lock, so concurrent transitions no
  longer silently drop history entries.
- `FlywayMigrationParityTest` runs the migrations against real Postgres with `ddl-auto: validate`,
  so entity/migration drift fails CI instead of production startup.

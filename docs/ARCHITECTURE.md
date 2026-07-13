# Project Forge - Architecture

## 1. Overview

Project Forge is a multi-tenant Enterprise SaaS Platform. This repository is the
foundation the team extends: a working vertical slice (tenancy, auth, RBAC, one
tenant-isolated resource module, audit logging) plus scaffolds for the features
planned next.

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

## 6. Scaffolded modules (not implemented - see inline `TODO(FRD-N.M)` comments)

- `billing/` - subscription plans & entitlement enforcement
- `notification/` - in-app + email notifications
- `dashboard/` - configurable dashboard widgets
- `publicapi/` - API-key-authenticated public endpoints (`/v1/public/**`)
- `sso/` - SAML/OIDC single sign-on

Each package's Javadoc references the FRD section it implements and the concrete next
steps. None of them contain business logic; controllers return `501 Not Implemented`.

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
- No custom role creation/editing (only the three system roles exist).
- No password reset / email verification flow.
- `@AfterThrowing` audit entries (failed attempts) are not recorded, only successes.

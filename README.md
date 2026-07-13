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
- **Frontend**: React + TypeScript + Tailwind + Zustand SPA - Signup, Login, Admin
  Console, Roles (reference), and a fully working Workflows page (create definitions,
  start instances, drive transitions) against the real API, with automatic access
  token refresh on 401.

## What's scaffolded only (TODOs, no business logic)

Subscription/billing & entitlement enforcement, notifications (in-app + email),
configurable dashboards, public API-key-authenticated endpoints, SAML/OIDC SSO. Each
lives in its own backend package (`billing/`, `notification/`, `dashboard/`,
`publicapi/`, `sso/`) with `TODO(FRD-N.M)` comments pointing at the relevant
requirement section; their controllers return `501 Not Implemented`.

## Verification status - read this before trusting the build

**This repository was built in a sandbox with no Java, Maven, Node.js, or git
toolchain available** (verified: `java`, `mvn`, `node`, `git` all absent from PATH and
from every common install location checked). As a direct consequence:

- The backend was **not compiled** and the test suite - including the required
  cross-tenant-isolation test (`TenantIsolationTest`) and the RBAC enforcement test
  (`RbacEnforcementTest`) - was **not run**.
- The frontend was **not built** with `npm`/`vite` and has not been type-checked by
  `tsc` outside of manual review.
- `git init` / `git commit` could **not** be performed here; there is no git history
  in this delivery. Initialize it yourself:
  ```
  git init
  git config user.name "Zentrox Engineering"
  git config user.email "engineering@zentroxglobaltechnologies.com"
  git checkout -b main   # or: git branch -m main, if init already defaulted to main
  git add -A
  git commit -m "Initial commit: Project Forge foundation - multi-tenancy, JWT+refresh auth, RBAC, workflow module, audit log"
  ```

**To compensate, every file in `backend/` was hand-reviewed at extra care** for
compile-correctness: package/directory consistency, brace/paren balance across all 80
Java files, Spring Data JPA repository wiring (the split `@EnableJpaRepositories`
scan for the tenant-scoped base repository class), Lombok builder inheritance (an
initial bug - plain `@Builder` silently dropping inherited `tenant_id`/`id` fields -
was caught this way and fixed with `@SuperBuilder`), entity/migration column parity,
and JWT/Jackson/Hibernate API usage against the pinned dependency versions in
`backend/pom.xml`. This is a real safety net, but it is not a substitute for actually
compiling and running `mvn verify`.

**First thing to do in an environment with the toolchain installed:**
```
cd backend && mvn -B verify
cd ../frontend && npm install && npm run build
```
Fix whatever `mvn verify` surfaces (if anything) before extending this codebase.

## Running locally (once verified)

```
cp .env.example .env   # fill in FORGE_JWT_SECRET and passwords
docker compose up --build
```

- Backend: http://localhost:8080 (OpenAPI UI at `/v1/swagger-ui.html`)
- Frontend: http://localhost:5173

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

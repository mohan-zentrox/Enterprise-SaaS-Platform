# Project Forge - Team

Per compliance policy, this repository refers to team members exclusively by their
role ID, never by name. Role IDs are assigned externally; do not invent new ones.

| Role ID   | Role                                | Primary areas in this repo                                            |
|-----------|--------------------------------------|-------------------------------------------------------------------------|
| T3-LEAD   | Tech Lead / Full-Stack Engineer      | Architecture, cross-cutting concerns (security, tenancy), code review  |
| T3-BE1    | Backend Engineer, Java               | `backend/` - Spring Boot services, repositories, migrations             |
| T3-FE1    | Frontend Engineer                    | `frontend/` - React/TypeScript SPA, API client, state management        |
| T3-DEV1   | Full-Stack Developer                 | Feature work spanning `backend/` and `frontend/`                        |
| T3-BA1    | Product / Business Analyst           | Requirements (BRD/FRD/SRS), acceptance criteria, `docs/`                |
| T3-QA1    | QA Engineer                          | Test strategy, `backend/src/test/**`, CI gating                        |

## Working agreements

- All schema changes go through Flyway migrations (`backend/src/main/resources/db/migration`),
  reviewed by T3-BE1 or T3-LEAD.
- Every write endpoint must carry a `@PreAuthorize` permission check (see
  `docs/ARCHITECTURE.md` Section 3) and, where it mutates tenant data, an `@Audited`
  annotation (Section 5).
- Tenant isolation is not optional or best-effort: new tenant-scoped entities must
  extend `TenantScopedEntity` and their repositories must extend
  `TenantScopedRepository` (see `docs/ARCHITECTURE.md` Section 2). PRs introducing a
  tenant-scoped entity without an accompanying isolation test should not be approved
  by T3-QA1.
- Scaffold packages (`billing`, `notification`, `dashboard`, `publicapi`, `sso`) carry
  `TODO(FRD-N.M)` comments pointing at the relevant FRD section - pick those up in
  FRD-section order unless T3-BA1/T3-LEAD reprioritize.

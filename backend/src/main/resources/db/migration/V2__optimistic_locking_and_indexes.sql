-- Project Forge - V2
-- FRD ref: Section 6 (Workflows), Section 8 (Audit)

-- ---------------------------------------------------------------------------
-- Optimistic locking on workflow instances.
--
-- A transition is read-modify-write over history_json: read the current state and history, append
-- an entry, write both back. Two concurrent transitions on the same instance therefore race, and
-- the slower write silently discards the faster one's history entry. The @Version column on
-- WorkflowInstance makes the second write fail with an optimistic-lock error (surfaced as 409)
-- instead of losing an audit-relevant event.
--
-- DEFAULT 0 backfills existing rows; NOT NULL is safe because of it.
-- ---------------------------------------------------------------------------
ALTER TABLE workflow_instances
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- Supporting indexes for the endpoints added alongside this migration.
-- ---------------------------------------------------------------------------

-- GET /v1/audit-logs pages by (tenant_id, occurred_at DESC). Without this the existing
-- idx_audit_logs_tenant_id gets the tenant filter but leaves the sort to a heap sort over every
-- row in the tenant - the audit table is the fastest-growing table in the schema.
CREATE INDEX idx_audit_logs_tenant_occurred_at
    ON audit_logs (tenant_id, occurred_at DESC);

-- GET /v1/users pages by (tenant_id, created_at DESC).
CREATE INDEX idx_users_tenant_created_at
    ON users (tenant_id, created_at DESC);

-- RoleManagementService blocks deletion of a role that still has members, which counts users by
-- (tenant_id, role_id); users.role_id has a foreign key but Postgres does not index FKs
-- automatically.
CREATE INDEX idx_users_tenant_role
    ON users (tenant_id, role_id);

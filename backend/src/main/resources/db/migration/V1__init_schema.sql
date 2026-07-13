-- Project Forge - initial schema
-- FRD ref: Section 3 (Multi-Tenancy), Section 4 (AuthN/AuthZ), Section 6 (Workflows), Section 8 (Audit)
--
-- Every tenant-owned table carries a mandatory tenant_id column and an index on it.
-- Row-level isolation is enforced in the application layer (see repository package,
-- TenantScopedRepository) and reinforced with a Hibernate @Filter at the entity layer.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- Tenants
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- ---------------------------------------------------------------------------
-- Roles (per-tenant; system roles OWNER/ADMIN/MEMBER seeded on tenant creation)
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);

CREATE TABLE role_permissions (
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission  VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role_id       UUID NOT NULL REFERENCES roles(id),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE INDEX idx_users_tenant_id ON users(tenant_id);

-- ---------------------------------------------------------------------------
-- Workflow definitions & instances (tenant-isolated resource module)
-- ---------------------------------------------------------------------------
CREATE TABLE workflow_definitions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(1000),
    definition_json  TEXT NOT NULL, -- {"states": [...], "transitions": [...]}
    version          INTEGER NOT NULL DEFAULT 1,
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workflow_def_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_workflow_definitions_tenant_id ON workflow_definitions(tenant_id);

CREATE TABLE workflow_instances (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    workflow_definition_id  UUID NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    current_state           VARCHAR(100) NOT NULL,
    history_json            TEXT NOT NULL DEFAULT '[]', -- [{"from":..,"to":..,"at":..,"by":..}]
    created_by              UUID NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workflow_instances_tenant_id ON workflow_instances(tenant_id);
CREATE INDEX idx_workflow_instances_definition_id ON workflow_instances(workflow_definition_id);

-- ---------------------------------------------------------------------------
-- Audit log (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    actor_user_id  UUID,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100) NOT NULL,
    entity_id      VARCHAR(100),
    details_json   TEXT,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_tenant_entity ON audit_logs(tenant_id, entity_type, entity_id);

-- Audit log is append-only at the application layer (service/repository never issue
-- UPDATE/DELETE against it); a DB-level trigger enforcing this is intentionally left
-- as a follow-up hardening task (see docs/ARCHITECTURE.md, "Hardening backlog").

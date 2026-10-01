-- Project Forge - V6
-- FRD ref: Section 11 (Configurable Dashboards)

CREATE TABLE dashboards (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    -- Nullable: a NULL owner is a dashboard shared with the whole tenant, a set owner is private to
    -- that user. Modelling "shared" as an absent owner rather than a boolean flag means there is no
    -- way to represent the contradictory state "shared but owned by nobody in particular".
    owner_user_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    layout_json    TEXT NOT NULL DEFAULT '{}',
    is_default     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dashboards_tenant_id ON dashboards(tenant_id);
CREATE INDEX idx_dashboards_tenant_owner ON dashboards (tenant_id, owner_user_id);

CREATE TABLE dashboard_widgets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    dashboard_id  UUID NOT NULL REFERENCES dashboards(id) ON DELETE CASCADE,
    widget_type   VARCHAR(100) NOT NULL,
    title         VARCHAR(255),
    config_json   TEXT NOT NULL DEFAULT '{}',
    position      INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_dashboard_widgets_position CHECK (position >= 0)
);

-- tenant_id is carried on the widget as well as the dashboard. Strictly it is derivable via the
-- dashboard, but every tenant-scoped entity in this schema has the column (see
-- TenantScopedEntity) and the Hibernate tenant filter applies per-table - a widget without it
-- could not be filtered without a join.
CREATE INDEX idx_dashboard_widgets_tenant_id ON dashboard_widgets(tenant_id);
CREATE INDEX idx_dashboard_widgets_dashboard ON dashboard_widgets (dashboard_id, position);

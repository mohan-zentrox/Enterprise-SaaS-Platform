-- Project Forge - V3
-- FRD ref: Section 10 (Notifications: in-app + email)

CREATE TABLE notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type              VARCHAR(100) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              VARCHAR(2000),
    -- Structured payload for the UI to link somewhere useful (entity type + id, etc.).
    payload_json      TEXT,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);

-- The inbox query is "my notifications, unread first, newest first". recipient_user_id leads
-- because a user only ever reads their own; tenant_id is still in the index so the tenant filter
-- is satisfied from the index rather than by rechecking rows.
CREATE INDEX idx_notifications_recipient_inbox
    ON notifications (tenant_id, recipient_user_id, created_at DESC);

-- Partial index for the unread badge count, which is requested on every page load and is far more
-- frequent than reading the inbox itself. Only unread rows are indexed, so it stays small even as
-- the table grows.
CREATE INDEX idx_notifications_unread
    ON notifications (tenant_id, recipient_user_id)
    WHERE read_at IS NULL;

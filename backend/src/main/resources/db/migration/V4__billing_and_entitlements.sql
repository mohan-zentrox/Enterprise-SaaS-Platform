-- Project Forge - V4
-- FRD ref: Section 9 (Subscription & Billing / Entitlement Enforcement)

-- ---------------------------------------------------------------------------
-- One subscription per tenant. The plan is the source of truth for limits; the columns here are
-- the *state* of the commercial relationship, which only the payment provider can tell us.
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan                     VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    status                   VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    -- The provider's own identifiers, so a webhook can be matched back to a tenant without
    -- trusting anything in the webhook payload except the id we already stored.
    provider                 VARCHAR(50),
    provider_customer_id     VARCHAR(255),
    provider_subscription_id VARCHAR(255),
    current_period_end       TIMESTAMPTZ,
    cancel_at_period_end     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A tenant with two subscriptions has no defined entitlement. Enforce single-subscription at
    -- the schema level rather than hoping application code never races.
    CONSTRAINT uq_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_subscriptions_plan CHECK (plan IN ('FREE', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    CONSTRAINT chk_subscriptions_status CHECK (status IN ('ACTIVE', 'TRIALING', 'PAST_DUE', 'CANCELED'))
);
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);

-- Lookup path for the webhook receiver: provider id -> tenant.
CREATE UNIQUE INDEX idx_subscriptions_provider_subscription
    ON subscriptions (provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Webhook idempotency.
--
-- Payment providers guarantee at-least-once delivery, so the same event WILL arrive twice. Without
-- this table a duplicate "subscription upgraded" is harmless but a duplicate "payment failed" or a
-- replayed out-of-order event is not. The primary key does the deduplication; the receiver inserts
-- before acting and treats a unique violation as "already handled".
-- ---------------------------------------------------------------------------
CREATE TABLE billing_webhook_events (
    provider      VARCHAR(50)  NOT NULL,
    event_id      VARCHAR(255) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, event_id)
);

-- ---------------------------------------------------------------------------
-- Usage counters, one row per (tenant, metric).
--
-- Deliberately a durable table rather than only Redis: a seat count that resets because a cache was
-- evicted would let a tenant exceed a limit they are paying not to exceed. Redis is the right place
-- for high-frequency metering on top of this (see EntitlementService), not for the ledger.
-- ---------------------------------------------------------------------------
CREATE TABLE tenant_usage (
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    metric      VARCHAR(100) NOT NULL,
    used        BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, metric),
    CONSTRAINT chk_tenant_usage_non_negative CHECK (used >= 0)
);

-- Every existing tenant gets a FREE subscription so entitlement checks have something to read.
-- Without this backfill, every tenant created before this migration would be unentitled to
-- everything the moment enforcement switches on.
INSERT INTO subscriptions (tenant_id, plan, status)
SELECT id, 'FREE', 'ACTIVE' FROM tenants;

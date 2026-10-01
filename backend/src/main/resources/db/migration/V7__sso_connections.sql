-- Project Forge - V7
-- FRD ref: Section 13 (SAML/OIDC SSO)

CREATE TABLE sso_connections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    protocol        VARCHAR(20) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,

    -- OIDC discovery + client registration. client_secret is encrypted at rest by the application
    -- (see SsoConnectionService) and is never returned by any endpoint.
    issuer          VARCHAR(500),
    client_id       VARCHAR(255),
    client_secret   VARCHAR(1000),
    authorization_endpoint VARCHAR(500),
    token_endpoint  VARCHAR(500),
    jwks_uri        VARCHAR(500),

    -- Which IdP claim carries the user's email, and the group-to-role mapping. Both per-tenant,
    -- because no two identity providers agree on either.
    email_claim     VARCHAR(100) NOT NULL DEFAULT 'email',
    groups_claim    VARCHAR(100),
    role_mapping_json TEXT NOT NULL DEFAULT '{}',

    -- When true, a user authenticated by the IdP who has no Forge account gets one created on first
    -- login. When false, SSO only authenticates users an administrator has already invited.
    auto_provision  BOOLEAN NOT NULL DEFAULT FALSE,
    default_role    VARCHAR(100) NOT NULL DEFAULT 'MEMBER',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One connection per tenant. Supporting several would mean the callback could not tell which
    -- one produced a given assertion without extra state.
    CONSTRAINT uq_sso_connections_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_sso_connections_protocol CHECK (protocol IN ('OIDC', 'SAML'))
);

CREATE INDEX idx_sso_connections_tenant_id ON sso_connections(tenant_id);

-- ---------------------------------------------------------------------------
-- Short-lived state for the OIDC authorization-code flow.
--
-- The `state` parameter is the CSRF defence for the redirect: without verifying it, an attacker can
-- feed a victim a callback URL carrying the attacker's own authorization code and log the victim
-- into the attacker's account (login CSRF). Stored server-side rather than in a cookie so the
-- tenant and nonce are bound to it and cannot be tampered with.
-- ---------------------------------------------------------------------------
CREATE TABLE sso_login_states (
    state        VARCHAR(128) PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nonce        VARCHAR(128) NOT NULL,
    redirect_uri VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ
);

CREATE INDEX idx_sso_login_states_expiry ON sso_login_states(expires_at);

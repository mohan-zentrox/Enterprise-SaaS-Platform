-- Project Forge - V5
-- FRD ref: Section 12 (Public API-Key-Authenticated Endpoints)

CREATE TABLE api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,

    -- The key is stored as an HMAC-SHA256 of the secret half, never in plaintext and never
    -- reversibly. A leaked database must not yield working credentials.
    --
    -- HMAC rather than bcrypt deliberately: this is verified on EVERY request to /v1/public/**, and
    -- bcrypt at a sane cost factor is milliseconds by design, which would make the hash the
    -- dominant cost of every call. The reason bcrypt is correct for passwords - deliberate slowness
    -- against guessing a low-entropy human secret - does not apply to a 256-bit random key, where
    -- brute force is infeasible regardless of hash speed.
    key_hash      VARCHAR(255) NOT NULL,

    -- The non-secret identifier embedded in the presented key, so verification is a single indexed
    -- lookup rather than a scan-and-compare over every key in the table.
    key_id        VARCHAR(64) NOT NULL,

    -- Last 4 characters of the secret, for the UI to render "sk_live_...a9f3" so a human can tell
    -- two keys apart without being shown either.
    key_suffix    VARCHAR(8) NOT NULL,

    scopes        VARCHAR(500) NOT NULL DEFAULT '',
    created_by    UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,

    CONSTRAINT uq_api_keys_key_id UNIQUE (key_id),
    CONSTRAINT uq_api_keys_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_api_keys_tenant_id ON api_keys(tenant_id);

-- Authentication path: resolve key_id -> row, for keys that are still usable.
CREATE INDEX idx_api_keys_active
    ON api_keys (key_id)
    WHERE revoked_at IS NULL;

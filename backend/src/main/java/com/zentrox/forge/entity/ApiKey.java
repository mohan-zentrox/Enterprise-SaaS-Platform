package com.zentrox.forge.entity;

import com.zentrox.forge.publicapi.ApiScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FRD Section 12 - an API key belonging to a tenant.
 *
 * The secret itself is never stored; {@code keyHash} is an HMAC of it (see ApiKeyService). Scopes
 * are a comma-separated string rather than an @ElementCollection table: the set is tiny, always
 * read whole, and read on every authenticated public-API request - a join per request to fetch
 * three enum values would be pure overhead.
 */
@Entity
@Table(name = "api_keys")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApiKey extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(name = "key_suffix", nullable = false, length = 8)
    private String keySuffix;

    @Column(nullable = false, length = 500)
    private String scopes = "";

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Set<ApiScope> scopeSet() {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ApiScope::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setScopeSet(Set<ApiScope> values) {
        this.scopes = values == null ? "" : values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** Revoked and expired keys are both unusable; expiry is checked against now. */
    public boolean isUsable(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}

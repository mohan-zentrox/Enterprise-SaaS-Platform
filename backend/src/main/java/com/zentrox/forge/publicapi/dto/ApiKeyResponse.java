package com.zentrox.forge.publicapi.dto;

import com.zentrox.forge.entity.ApiKey;
import com.zentrox.forge.publicapi.ApiScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Metadata only - there is no field for the secret, because it is not stored and cannot be shown
 * again. {@code maskedKey} is enough for a human to identify which key a row refers to.
 */
public record ApiKeyResponse(UUID id, String name, String maskedKey, List<ApiScope> scopes,
                              Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                              Instant revokedAt, boolean active) {

    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getName(),
                "forge_" + key.getKeyId() + "_..." + key.getKeySuffix(),
                key.scopeSet().stream().toList(),
                key.getCreatedAt(),
                key.getLastUsedAt(),
                key.getExpiresAt(),
                key.getRevokedAt(),
                key.isUsable(Instant.now()));
    }
}

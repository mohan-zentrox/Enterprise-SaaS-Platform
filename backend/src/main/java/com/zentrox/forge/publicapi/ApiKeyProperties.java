package com.zentrox.forge.publicapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forge.api-keys.*}.
 *
 * {@code hashingKey} is the pepper for the keyed hash of API-key secrets. It is separate from the
 * JWT secret so the two can be rotated independently: rotating the JWT secret only invalidates
 * access tokens (users re-authenticate transparently), whereas rotating this one invalidates every
 * API key in existence and breaks every customer integration. Sharing one value would couple a
 * routine operation to a disruptive one.
 *
 * {@code rateLimitPerMinute} applies per key. Zero disables rate limiting.
 */
@ConfigurationProperties(prefix = "forge.api-keys")
public record ApiKeyProperties(String hashingKey, int rateLimitPerMinute) {

    public ApiKeyProperties {
        if (hashingKey == null || hashingKey.isBlank()) {
            throw new IllegalStateException(
                    "forge.api-keys.hashing-key must be set. Without it, API key hashes are not "
                            + "peppered and a stolen database can be attacked offline.");
        }
        if (rateLimitPerMinute < 0) {
            rateLimitPerMinute = 0;
        }
    }

    public boolean rateLimitingEnabled() {
        return rateLimitPerMinute > 0;
    }
}

package com.zentrox.forge.service;

import com.zentrox.forge.security.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens tracked in Redis (FRD Section 4: "refresh tokens
 * tracked/revocable in Redis"). A refresh token is never a JWT - it carries no
 * information about the user by itself, which is what makes server-side revocation
 * (logout, admin-forced logout, rotation-on-reuse) actually work: possession of the
 * opaque string is meaningless once its Redis key is deleted.
 *
 * Key shape: {@code <prefix><token> -> "<tenantId>:<userId>"}, TTL = ttlDays.
 * Rotation: every refresh call deletes the presented token and issues a brand new one
 * (rotation-on-use), which also limits the blast radius of a stolen refresh token.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;
    private final RefreshTokenProperties properties;

    public String issue(UUID tenantId, UUID userId) {
        String token = generateOpaqueToken();
        String key = key(token);
        String value = tenantId + ":" + userId;
        redisTemplate.opsForValue().set(key, value, Duration.ofDays(properties.ttlDays()));
        return token;
    }

    /** Returns the (tenantId, userId) the token was issued for, if it exists and hasn't expired/been revoked. */
    public Optional<TokenOwner> resolve(String token) {
        String value = redisTemplate.opsForValue().get(key(token));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        return Optional.of(new TokenOwner(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
    }

    /** Rotation-on-use: invalidate the presented token and mint a replacement for the same user. */
    public String rotate(String presentedToken, UUID tenantId, UUID userId) {
        revoke(presentedToken);
        return issue(tenantId, userId);
    }

    public void revoke(String token) {
        redisTemplate.delete(key(token));
    }

    private String key(String token) {
        return properties.redisKeyPrefix() + token;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenOwner(UUID tenantId, UUID userId) {
    }
}

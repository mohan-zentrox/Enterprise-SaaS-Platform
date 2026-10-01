package com.zentrox.forge.publicapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-key rate limiting on the public API (FRD-12.4), using the Redis connection that already backs
 * refresh tokens.
 *
 * <p><b>Fixed window, not a token bucket.</b> A window key is {@code apikey:rl:<keyId>:<epochMinute>},
 * incremented per request and expired automatically. This permits a burst of up to 2x the limit
 * across a window boundary, which a sliding window would not - accepted deliberately, because the
 * alternative costs either a sorted set per key (memory proportional to request volume) or Lua
 * scripting, and the purpose here is to stop runaway integrations rather than to shape traffic
 * precisely.
 *
 * <p><b>Fails open.</b> If Redis is unreachable the request is allowed. Rate limiting protects
 * against excess load; it is not an authorization control, and letting a Redis outage take down every
 * customer integration would cause far more harm than the excess traffic it prevents. The failure is
 * logged so the outage is visible.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyRateLimiter {

    private static final String KEY_PREFIX = "forge:apikey:rl:";

    private final StringRedisTemplate redisTemplate;
    private final ApiKeyProperties properties;

    /** @return true if the request is within the limit and may proceed */
    public boolean tryConsume(UUID apiKeyId) {
        if (!properties.rateLimitingEnabled()) {
            return true;
        }

        long window = Instant.now().getEpochSecond() / 60;
        String key = KEY_PREFIX + apiKeyId + ":" + window;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                // Only the first request in a window sets the TTL, so a busy key does not keep
                // pushing its own expiry forward and never resetting.
                redisTemplate.expire(key, Duration.ofSeconds(120));
            }
            return count <= properties.rateLimitPerMinute();
        } catch (RuntimeException e) {
            log.error("Rate limiter unavailable, allowing request for key {}: {}", apiKeyId, e.getMessage());
            return true;
        }
    }

    public int limitPerMinute() {
        return properties.rateLimitPerMinute();
    }
}

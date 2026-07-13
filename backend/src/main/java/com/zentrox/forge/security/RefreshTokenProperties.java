package com.zentrox.forge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forge.refresh-token")
public record RefreshTokenProperties(long ttlDays, String redisKeyPrefix) {
}

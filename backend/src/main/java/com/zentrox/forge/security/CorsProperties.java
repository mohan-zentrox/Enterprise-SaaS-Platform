package com.zentrox.forge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code forge.cors.allowed-origins} (see application.yml / .env.example), which was
 * previously declared in configuration and read by nothing - SecurityConfig hardcoded
 * {@code allowedOriginPatterns("*")} while also setting {@code allowCredentials(true)}.
 *
 * Origins are exact values, not patterns: a credentialed CORS response may not echo a
 * wildcard origin, so an explicit list is the only correct configuration for this API.
 */
@ConfigurationProperties(prefix = "forge.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("http://localhost:5173")
                : List.copyOf(allowedOrigins);
    }
}

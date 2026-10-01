package com.zentrox.forge.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forge.sso.*}.
 *
 * <p>{@code baseUrl} is the externally reachable origin of this API. It cannot be derived from the
 * incoming request: the redirect URI registered at the IdP must match byte-for-byte, and behind a
 * proxy or load balancer {@code request.getRequestURL()} reports the internal host - which would
 * produce a redirect URI the IdP rejects, or worse, one an attacker can influence via the Host
 * header.
 *
 * <p>{@code encryptionKey} protects IdP client secrets at rest. Separate from every other secret in
 * the system so its rotation blast radius is exactly "re-enter IdP secrets" rather than also
 * invalidating sessions or API keys.
 *
 * <p>{@code loginStateTtlSeconds} bounds how long an authorization request may sit unredeemed. Short
 * by default: the window is a user's redirect to their IdP and back, measured in seconds, and a long
 * TTL only widens the period in which a captured state value is useful.
 */
@ConfigurationProperties(prefix = "forge.sso")
public record SsoProperties(String baseUrl, String encryptionKey, long loginStateTtlSeconds) {

    public SsoProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : stripTrailingSlash(baseUrl);
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "forge.sso.encryption-key must be set. IdP client secrets are stored encrypted "
                            + "with it, and a blank key would mean storing them in plaintext.");
        }
        if (loginStateTtlSeconds <= 0) {
            loginStateTtlSeconds = 300;
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

package com.zentrox.forge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forge.jwt")
public record JwtProperties(String secret, long accessTokenTtlMinutes, String issuer) {
}

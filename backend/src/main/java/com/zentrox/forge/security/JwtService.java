package com.zentrox.forge.security;

import com.zentrox.forge.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates short-lived JWT access tokens. Refresh tokens are handled
 * separately by {@link com.zentrox.forge.service.RefreshTokenService} (opaque,
 * Redis-backed, revocable) - they are never JWTs, precisely so they can be revoked
 * server-side.
 */
@Service
public class JwtService {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        Set<String> permissionNames = user.getRole().getPermissions().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .claim(CLAIM_TENANT_ID, user.getTenantId().toString())
                .claim(CLAIM_ROLE, user.getRole().getName())
                .claim(CLAIM_PERMISSIONS, List.copyOf(permissionNames))
                .claim(CLAIM_EMAIL, user.getEmail())
                .signWith(signingKey)
                .compact();
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has a bad signature. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public CustomUserDetails toUserDetails(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        UUID tenantId = UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class));
        String role = claims.get(CLAIM_ROLE, String.class);
        String email = claims.get(CLAIM_EMAIL, String.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);
        return new CustomUserDetails(userId, tenantId, email, role, Set.copyOf(permissions));
    }
}

package com.zentrox.forge.sso.dto;

import com.zentrox.forge.entity.SsoConnection;
import com.zentrox.forge.sso.SsoProtocol;

import java.time.Instant;
import java.util.UUID;

/**
 * Note the absence of {@code clientSecret} and the presence of {@code clientSecretSet} instead. An
 * administration UI needs to know whether a secret has been configured; it never needs the value,
 * and returning it would put a live IdP credential into browser memory, logs and any proxy in
 * between.
 */
public record SsoConnectionResponse(UUID id, SsoProtocol protocol, boolean enabled, String issuer,
                                     String clientId, boolean clientSecretSet,
                                     String authorizationEndpoint, String tokenEndpoint, String jwksUri,
                                     String emailClaim, String groupsClaim, String roleMappingJson,
                                     boolean autoProvision, String defaultRole,
                                     String loginUrl, String callbackUrl,
                                     Instant createdAt, Instant updatedAt) {

    public static SsoConnectionResponse from(SsoConnection c, String tenantSlug, String baseUrl) {
        return new SsoConnectionResponse(
                c.getId(),
                c.getProtocol(),
                c.isEnabled(),
                c.getIssuer(),
                c.getClientId(),
                c.getClientSecret() != null && !c.getClientSecret().isBlank(),
                c.getAuthorizationEndpoint(),
                c.getTokenEndpoint(),
                c.getJwksUri(),
                c.getEmailClaim(),
                c.getGroupsClaim(),
                c.getRoleMappingJson(),
                c.isAutoProvision(),
                c.getDefaultRole(),
                // Surfaced so an administrator can copy them into their IdP rather than guess.
                baseUrl + "/v1/sso/" + tenantSlug + "/login",
                baseUrl + "/v1/sso/" + tenantSlug + "/callback",
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}

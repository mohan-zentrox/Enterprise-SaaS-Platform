package com.zentrox.forge.sso;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.entity.SsoConnection;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.SsoConnectionRepository;
import com.zentrox.forge.sso.dto.SsoConnectionRequest;
import com.zentrox.forge.sso.dto.SsoConnectionResponse;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * FRD-13.1 - a tenant administering its own IdP connection.
 *
 * Always the caller's own tenant, resolved from {@link TenantContext}; there is no endpoint that
 * names a tenant, so no administrator can point another organization's SSO at their own IdP.
 */
@Service
@RequiredArgsConstructor
public class SsoConnectionService {

    private final SsoConnectionRepository connectionRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final SecretCipher secretCipher;
    private final SsoProperties ssoProperties;

    public SsoConnectionResponse getConnection() {
        UUID tenantId = TenantContext.requireTenantId();
        SsoConnection connection = connectionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("No SSO connection is configured for this organization"));
        return toResponse(connection, tenantId);
    }

    @Audited(action = "SSO_CONNECTION_UPDATE", entityType = "SsoConnection")
    @Transactional
    public SsoConnectionResponse upsertConnection(SsoConnectionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        SsoConnection connection = connectionRepository.findByTenantId(tenantId)
                .orElseGet(() -> SsoConnection.builder().tenantId(tenantId).build());

        connection.setProtocol(request.protocol());
        connection.setIssuer(request.issuer());
        connection.setClientId(request.clientId());
        connection.setAuthorizationEndpoint(request.authorizationEndpoint());
        connection.setTokenEndpoint(request.tokenEndpoint());
        connection.setJwksUri(request.jwksUri());
        connection.setEmailClaim(blankToDefault(request.emailClaim(), "email"));
        connection.setGroupsClaim(request.groupsClaim());
        connection.setRoleMappingJson(blankToDefault(request.roleMappingJson(), "{}"));
        connection.setAutoProvision(request.autoProvision());
        connection.setDefaultRole(blankToDefault(request.defaultRole(), "MEMBER"));

        // Write-only: an omitted secret leaves the stored one alone, so saving an edited form does
        // not wipe a credential the UI was never shown.
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            connection.setClientSecret(secretCipher.encrypt(request.clientSecret()));
        }

        if (request.enabled()) {
            validateEnableable(tenantId, connection);
        }
        connection.setEnabled(request.enabled());

        return toResponse(connectionRepository.save(connection), tenantId);
    }

    @Audited(action = "SSO_CONNECTION_DELETE", entityType = "SsoConnection")
    @Transactional
    public void deleteConnection() {
        UUID tenantId = TenantContext.requireTenantId();
        SsoConnection connection = connectionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("No SSO connection is configured for this organization"));
        connectionRepository.deleteByIdAndTenantId(connection.getId(), tenantId);
    }

    /**
     * Refuses to enable a connection that cannot work.
     *
     * A half-configured but "enabled" connection is worse than a disabled one: users are redirected
     * to an IdP that rejects them, and the failure surfaces as an opaque error mid-login rather than
     * as a validation message to the administrator who caused it.
     */
    private void validateEnableable(UUID tenantId, SsoConnection connection) {
        if (connection.getProtocol() == SsoProtocol.SAML) {
            // Honest refusal rather than a silent no-op: the column and the enum exist so the API
            // does not have to change when SAML lands, but the protocol is not implemented.
            throw new ConflictException(
                    "SAML is not implemented yet; only OIDC connections can be enabled. "
                            + "The configuration can be stored, but not activated.");
        }

        require(connection.getIssuer(), "issuer");
        require(connection.getClientId(), "clientId");
        require(connection.getClientSecret(), "clientSecret");
        require(connection.getAuthorizationEndpoint(), "authorizationEndpoint");
        require(connection.getTokenEndpoint(), "tokenEndpoint");

        // A default role that does not exist would make every auto-provisioned login fail at the
        // last step, after the user has already authenticated successfully.
        if (roleRepository.findByTenantIdAndName(tenantId, connection.getDefaultRole()).isEmpty()) {
            throw new ConflictException(
                    "Default role '" + connection.getDefaultRole() + "' does not exist in this organization");
        }
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConflictException("Cannot enable SSO without " + field);
        }
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private SsoConnectionResponse toResponse(SsoConnection connection, UUID tenantId) {
        String slug = tenantRepository.findById(tenantId).map(Tenant::getSlug).orElse("unknown");
        return SsoConnectionResponse.from(connection, slug, ssoProperties.baseUrl());
    }
}

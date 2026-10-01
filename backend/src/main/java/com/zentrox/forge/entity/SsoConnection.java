package com.zentrox.forge.entity;

import com.zentrox.forge.sso.SsoProtocol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * FRD Section 13 - one tenant's identity-provider configuration.
 *
 * Per-tenant by design (FRD-13.4): each customer brings their own IdP, so there is no global SSO
 * configuration anywhere in this system. {@code clientSecret} is stored encrypted and never returned
 * by an endpoint - see SsoConnectionService.
 */
@Entity
@Table(name = "sso_connections")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SsoConnection extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SsoProtocol protocol;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(length = 500)
    private String issuer;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret", length = 1000)
    private String clientSecret;

    @Column(name = "authorization_endpoint", length = 500)
    private String authorizationEndpoint;

    @Column(name = "token_endpoint", length = 500)
    private String tokenEndpoint;

    @Column(name = "jwks_uri", length = 500)
    private String jwksUri;

    @Column(name = "email_claim", nullable = false, length = 100)
    @Builder.Default
    private String emailClaim = "email";

    @Column(name = "groups_claim", length = 100)
    private String groupsClaim;

    @Column(name = "role_mapping_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String roleMappingJson = "{}";

    @Column(name = "auto_provision", nullable = false)
    @Builder.Default
    private boolean autoProvision = false;

    @Column(name = "default_role", nullable = false, length = 100)
    @Builder.Default
    private String defaultRole = "MEMBER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

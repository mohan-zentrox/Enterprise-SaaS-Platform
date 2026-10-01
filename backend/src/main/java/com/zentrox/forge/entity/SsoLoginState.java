package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One in-flight OIDC login.
 *
 * Not tenant-scoped in the TenantScopedEntity sense: the callback arrives unauthenticated with only
 * the {@code state} value, and looking the tenant up from it is the whole purpose of this row. Its
 * primary key is the opaque state string rather than a surrogate UUID, because that is the value the
 * IdP echoes back.
 */
@Entity
@Table(name = "sso_login_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoLoginState {

    @Id
    @Column(length = 128)
    private String state;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 128)
    private String nonce;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the state is redeemed, so a code cannot be replayed with the same state. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}

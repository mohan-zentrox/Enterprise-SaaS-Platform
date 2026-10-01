package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/**
 * FRD Section 11 - a dashboard belonging to a tenant, optionally owned by one user.
 *
 * A null {@code ownerUserId} means the dashboard is shared across the tenant; a set one means it is
 * private to that user. See V6__dashboards.sql for why this is an absent owner rather than a flag.
 */
@Entity
@Table(name = "dashboards")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Dashboard extends TenantScopedEntity {

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(nullable = false)
    private String name;

    // See WorkflowDefinition#definitionJson for why TEXT rather than @Lob.
    @Column(name = "layout_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String layoutJson = "{}";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultDashboard = false;

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

    public boolean isShared() {
        return ownerUserId == null;
    }
}

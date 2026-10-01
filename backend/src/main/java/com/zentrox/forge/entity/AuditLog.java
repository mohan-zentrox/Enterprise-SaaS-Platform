package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail entry. Written exclusively by {@link com.zentrox.forge.aop.AuditAspect}
 * via {@link com.zentrox.forge.repository.tenant.AuditLogRepository#save}. Nothing in this
 * codebase updates or deletes an AuditLog row - the repository intentionally exposes no
 * update/delete helpers beyond what JpaRepository provides, and no service calls them.
 */
@Entity
@Table(name = "audit_logs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuditLog extends TenantScopedEntity {

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    // See WorkflowDefinition#definitionJson for why this is not @Lob.
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}

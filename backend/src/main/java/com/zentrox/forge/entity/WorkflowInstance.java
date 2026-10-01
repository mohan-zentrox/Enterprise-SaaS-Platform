package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * A running instance of a {@link WorkflowDefinition}: current state plus an
 * append-only JSON transition history:
 * [{"from":"DRAFT","to":"IN_REVIEW","at":"...","by":"<userId>"}, ...]
 */
@Entity
@Table(name = "workflow_instances")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class WorkflowInstance extends TenantScopedEntity {

    @Column(name = "workflow_definition_id", nullable = false)
    private UUID workflowDefinitionId;

    @Column(name = "current_state", nullable = false, length = 100)
    private String currentState;

    // See WorkflowDefinition#definitionJson for why this is not @Lob.
    @Column(name = "history_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String historyJson = "[]";

    /**
     * Optimistic lock. Without it, two concurrent transitions each read history_json, append their
     * own entry, and write it back - the slower write wins and the other transition disappears from
     * the history entirely. Named lock_version rather than version to avoid confusion with
     * {@link WorkflowDefinition#getVersion()}, which is a business-visible definition revision.
     */
    @Version
    @Column(name = "lock_version", nullable = false)
    @Builder.Default
    private long lockVersion = 0L;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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

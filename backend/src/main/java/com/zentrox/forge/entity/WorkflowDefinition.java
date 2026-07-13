package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant-owned workflow definition: a set of states and allowed transitions,
 * stored as JSON text (portable across H2-in-tests and Postgres-in-prod).
 * Example definitionJson shape:
 * {"states":["DRAFT","IN_REVIEW","APPROVED","REJECTED"],
 *  "transitions":[{"from":"DRAFT","to":"IN_REVIEW"},{"from":"IN_REVIEW","to":"APPROVED"},
 *                 {"from":"IN_REVIEW","to":"REJECTED"}]}
 */
@Entity
@Table(name = "workflow_definitions", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class WorkflowDefinition extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    // Deliberately NOT @Lob: on PostgreSQL, Hibernate 6 maps @Lob String fields to the
    // JDBC CLOB API, which doesn't play well with a plain `TEXT` column (see V1 migration).
    // columnDefinition documents intent; actual DDL/type ownership stays with Flyway.
    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

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

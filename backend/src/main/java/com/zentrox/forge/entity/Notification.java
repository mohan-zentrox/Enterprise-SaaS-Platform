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
 * FRD Section 10 - an in-app notification addressed to one user inside one tenant.
 *
 * {@code readAt} is a nullable timestamp rather than a boolean flag: "when did they read it" is a
 * strict superset of "have they read it", costs the same storage, and answers questions the boolean
 * cannot (time-to-acknowledge, and whether a re-read should be recorded).
 */
@Entity
@Table(name = "notifications")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Notification extends TenantScopedEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String body;

    // See WorkflowDefinition#definitionJson for why this is TEXT and not @Lob.
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}

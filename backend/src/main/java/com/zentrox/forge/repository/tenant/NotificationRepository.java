package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends TenantScopedRepository<Notification, UUID> {

    Page<Notification> findAllByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientUserId, Pageable pageable);

    long countByTenantIdAndRecipientUserIdAndReadAtIsNull(UUID tenantId, UUID recipientUserId);

    /**
     * Scoped by recipient as well as id: a user must not be able to mark someone else's
     * notification read, even inside their own tenant.
     */
    Optional<Notification> findByIdAndTenantIdAndRecipientUserId(UUID id, UUID tenantId, UUID recipientUserId);

    /**
     * Bulk "mark all read" as a single UPDATE. Loading every unread row into memory just to set one
     * field would be O(unread) round-trips and object churn for no benefit - nothing here needs the
     * entities. {@code read_at IS NULL} in the predicate makes it idempotent and keeps it off rows
     * that were already read (so their original timestamp survives).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.readAt = :readAt
             WHERE n.tenantId = :tenantId
               AND n.recipientUserId = :recipientUserId
               AND n.readAt IS NULL
            """)
    int markAllRead(@Param("tenantId") UUID tenantId,
                    @Param("recipientUserId") UUID recipientUserId,
                    @Param("readAt") Instant readAt);
}

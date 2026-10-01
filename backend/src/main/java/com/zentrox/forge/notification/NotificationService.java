package com.zentrox.forge.notification;

import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.entity.Notification;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.notification.dto.NotificationResponse;
import com.zentrox.forge.notification.email.EmailMessage;
import com.zentrox.forge.notification.email.EmailSender;
import com.zentrox.forge.repository.tenant.NotificationRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FRD Section 10 - in-app notifications and email.
 *
 * Two deliberate choices:
 *
 * <ul>
 *   <li><b>Writes take an explicit tenantId</b> rather than reading {@link TenantContext}. These
 *       methods are called from {@code @Async} event listeners, which run on a different thread -
 *       and TenantContext is a ThreadLocal, so it is empty there. Passing the tenant explicitly is
 *       what makes async delivery correct instead of intermittently unattributed.</li>
 *   <li><b>Reads use TenantContext</b>, because those are ordinary request-thread calls.</li>
 * </ul>
 *
 * Delivery failure never propagates: a notification is a side effect of a business operation, and
 * must not be able to fail it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    // ------------------------------------------------------------------ writing

    /**
     * Creates an in-app notification. Returns the saved id, or null if delivery was skipped or
     * failed - callers are side-effect triggers and have nothing useful to do with a failure.
     */
    @Transactional
    public UUID notifyInApp(UUID tenantId, UUID recipientUserId, String type, String title, String body,
                             String payloadJson) {
        try {
            Notification notification = Notification.builder()
                    .tenantId(tenantId)
                    .recipientUserId(recipientUserId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .payloadJson(payloadJson)
                    .build();
            return notificationRepository.save(notification).getId();
        } catch (RuntimeException e) {
            log.error("Failed to create {} notification for user {} in tenant {}: {}",
                    type, recipientUserId, tenantId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Queues an email. {@code @Async} so a slow or unreachable SMTP relay cannot add its latency to
     * the request that triggered it - see ForgeApplication's {@code @EnableAsync}.
     */
    @Async
    public void notifyByEmail(String recipientEmail, String subject, String body) {
        try {
            emailSender.send(new EmailMessage(recipientEmail, subject, body));
        } catch (RuntimeException e) {
            log.error("Failed to send email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    /** Convenience for the common case: record it in the inbox AND email the same content. */
    public void notifyBothChannels(UUID tenantId, UUID recipientUserId, String type, String title, String body,
                                    String payloadJson) {
        notifyInApp(tenantId, recipientUserId, type, title, body, payloadJson);
        userRepository.findByIdAndTenantId(recipientUserId, tenantId)
                .map(User::getEmail)
                .ifPresent(email -> notifyByEmail(email, title, body));
    }

    // ------------------------------------------------------------------ reading

    public PageResponse<NotificationResponse> listInbox(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = currentUserId();
        return PageResponse.from(
                notificationRepository.findAllByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(
                        tenantId, userId, pageable),
                NotificationResponse::from);
    }

    public long unreadCount() {
        UUID tenantId = TenantContext.requireTenantId();
        return notificationRepository.countByTenantIdAndRecipientUserIdAndReadAtIsNull(tenantId, currentUserId());
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = currentUserId();

        Notification notification = notificationRepository
                .findByIdAndTenantIdAndRecipientUserId(id, tenantId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + id));

        // Idempotent: re-reading must not overwrite the original acknowledgement time.
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    /** Returns how many were newly marked read. */
    @Transactional
    public int markAllRead() {
        UUID tenantId = TenantContext.requireTenantId();
        return notificationRepository.markAllRead(tenantId, currentUserId(), Instant.now());
    }

    /**
     * A notification inbox is strictly personal, so there is no permission that grants access to
     * someone else's - the recipient is always the caller, taken from the token.
     */
    private UUID currentUserId() {
        return com.zentrox.forge.security.SecurityUtils.currentUserId()
                .orElseThrow(() -> new IllegalStateException(
                        "No authenticated user bound to the request; a notification inbox is per-user"));
    }
}

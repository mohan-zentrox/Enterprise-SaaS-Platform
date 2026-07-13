package com.zentrox.forge.notification;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SCAFFOLD ONLY - FRD Section 10 (Notifications: in-app + email).
 *
 * TODO(FRD-10.1): Define a `notifications` table (tenant_id, recipient_user_id, type,
 *   payload_json, read_at, created_at) and an in-app inbox endpoint.
 * TODO(FRD-10.2): Add an email channel via a provider abstraction (e.g. Spring Mail /
 *   SES/SendGrid) with templated messages; queue sends asynchronously (see
 *   ForgeApplication's @EnableAsync) rather than blocking the triggering request.
 * TODO(FRD-10.3): Hook into domain events (workflow transitions, user invites) once an
 *   event/outbox mechanism exists - do not call this service directly from deep inside
 *   business logic to avoid tight coupling; prefer an ApplicationEventPublisher.
 */
@Service
public class NotificationService {

    public void notifyInApp(UUID tenantId, UUID recipientUserId, String type, String payloadJson) {
        throw new UnsupportedOperationException("In-app notifications are not implemented yet - see FRD Section 10");
    }

    public void notifyByEmail(UUID tenantId, String recipientEmail, String templateKey, String payloadJson) {
        throw new UnsupportedOperationException("Email notifications are not implemented yet - see FRD Section 10");
    }
}

package com.zentrox.forge.notification.dto;

import com.zentrox.forge.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String title, String body, String payloadJson,
                                    boolean read, Instant readAt, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getPayloadJson(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}

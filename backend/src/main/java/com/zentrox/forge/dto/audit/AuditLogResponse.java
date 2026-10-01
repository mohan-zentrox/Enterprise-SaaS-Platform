package com.zentrox.forge.dto.audit;

import com.zentrox.forge.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, String entityId,
                                String detailsJson, Instant occurredAt) {

    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(entry.getId(), entry.getActorUserId(), entry.getAction(),
                entry.getEntityType(), entry.getEntityId(), entry.getDetailsJson(), entry.getTimestamp());
    }
}

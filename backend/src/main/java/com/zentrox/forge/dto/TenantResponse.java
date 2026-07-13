package com.zentrox.forge.dto;

import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, TenantStatus status, Instant createdAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus(),
                tenant.getCreatedAt());
    }
}

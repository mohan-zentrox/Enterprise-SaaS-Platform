package com.zentrox.forge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /v1/tenants/current (permission TENANT_UPDATE). Only the display name is mutable: the slug
 * is part of every user's login identity and appears in refresh-token bookkeeping, so renaming it
 * is a migration, not an edit.
 */
public record TenantUpdateRequest(

        @NotBlank
        @Size(max = 255)
        String name
) {
}

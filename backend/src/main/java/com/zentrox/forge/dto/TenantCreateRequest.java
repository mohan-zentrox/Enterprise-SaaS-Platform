package com.zentrox.forge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /v1/tenants payload. Creates the Tenant AND its first Owner user in a single
 * transaction - see TenantService#createTenant.
 */
public record TenantCreateRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug must be lowercase, alphanumeric, hyphen-separated")
        String slug,

        @NotBlank
        @Size(max = 255)
        String ownerFullName,

        @NotBlank
        @Email
        @Size(max = 255)
        String ownerEmail,

        @NotBlank
        @Size(min = 8, max = 255, message = "password must be at least 8 characters")
        String ownerPassword
) {
}

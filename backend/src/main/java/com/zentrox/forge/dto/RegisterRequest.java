package com.zentrox.forge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /v1/auth/register - creates a MEMBER user under an existing tenant. */
public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String tenantSlug,

        @NotBlank
        @Size(max = 255)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 255, message = "password must be at least 8 characters")
        String password
) {
}

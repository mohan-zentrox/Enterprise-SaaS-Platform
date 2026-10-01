package com.zentrox.forge.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /v1/users - an administrator adds a user to their own tenant (permission USER_INVITE).
 * This is the sanctioned replacement for public self-registration, which is disabled by default
 * (see RegistrationProperties).
 *
 * The tenant is never taken from the request body: it comes from the caller's JWT via
 * TenantContext, so an administrator of tenant A cannot create a user in tenant B.
 *
 * {@code initialPassword} is set by the inviting administrator and must be changed by the user.
 * A proper invitation-token + email flow is the follow-up (see docs/ARCHITECTURE.md).
 */
public record UserCreateRequest(

        @NotBlank
        @Size(max = 255)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 255, message = "initialPassword must be at least 8 characters")
        String initialPassword,

        @NotBlank
        @Size(max = 100)
        String roleName
) {
}

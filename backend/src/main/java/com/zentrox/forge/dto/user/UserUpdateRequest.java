package com.zentrox.forge.dto.user;

import jakarta.validation.constraints.Size;

/**
 * PATCH /v1/users/{id} (permission USER_UPDATE). Both fields are optional; a null field is left
 * unchanged. Email is deliberately not updatable here - it is the login identity, and changing it
 * needs a verification flow rather than a silent overwrite.
 */
public record UserUpdateRequest(

        @Size(max = 255)
        String fullName,

        @Size(max = 100)
        String roleName
) {
}

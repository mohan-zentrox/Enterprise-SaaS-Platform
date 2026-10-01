package com.zentrox.forge.publicapi.dto;

import com.zentrox.forge.publicapi.ApiScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** {@code expiresAt} is optional; null means the key does not expire. */
public record ApiKeyCreateRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotEmpty(message = "grant at least one scope")
        Set<ApiScope> scopes,

        Instant expiresAt
) {
}

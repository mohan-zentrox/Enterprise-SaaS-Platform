package com.zentrox.forge.dto.role;

import com.zentrox.forge.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * POST /v1/roles and PUT /v1/roles/{id} (permission ROLE_MANAGE) - custom per-tenant roles.
 *
 * Permissions are bound as the {@link Permission} enum, so an unknown permission name is rejected
 * as a 400 by Jackson rather than silently dropped. A role may only be granted permissions the
 * caller themselves holds - see RoleManagementService, which enforces that no administrator can
 * mint a role more powerful than their own.
 */
public record RoleWriteRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        Set<Permission> permissions
) {
}

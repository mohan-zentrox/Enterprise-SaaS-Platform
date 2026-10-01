package com.zentrox.forge.dto.role;

import com.zentrox.forge.entity.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, boolean systemRole, List<String> permissions,
                            Instant createdAt) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.isSystemRole(),
                role.getPermissions().stream().map(Enum::name).sorted().toList(),
                role.getCreatedAt());
    }
}

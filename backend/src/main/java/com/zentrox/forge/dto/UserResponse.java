package com.zentrox.forge.dto;

import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String role, UserStatus status,
                            Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName(),
                user.getStatus(), user.getCreatedAt());
    }
}

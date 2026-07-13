package com.zentrox.forge.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Convenience accessors for the current authenticated principal, used by the audit aspect and services. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<CustomUserDetails> currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static Optional<UUID> currentUserId() {
        return currentUser().map(CustomUserDetails::getUserId);
    }
}

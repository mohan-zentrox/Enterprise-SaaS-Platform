package com.zentrox.forge.notification.event;

import java.util.UUID;

/**
 * Published by UserManagementService after a user is created by an administrator.
 *
 * Carries the tenant explicitly because the listener runs on an @Async thread where TenantContext
 * (a ThreadLocal) is not populated.
 */
public record UserInvitedEvent(UUID tenantId, UUID userId, String email, String fullName, String roleName,
                                String organizationName) {
}

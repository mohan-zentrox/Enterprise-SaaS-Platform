package com.zentrox.forge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether {@code POST /v1/auth/register} accepts public self-registration.
 *
 * Defaults to {@code false}: on an enterprise platform, knowing a tenant's slug must not be
 * enough to create an account inside that tenant. The sanctioned path for adding a user is
 * {@code POST /v1/users} guarded by {@code USER_INVITE} (see UserController), which requires an
 * authenticated administrator of that tenant.
 *
 * Deployments that genuinely want open sign-up (a free tier, a demo environment) can set
 * {@code forge.registration.self-service-enabled=true}.
 */
@ConfigurationProperties(prefix = "forge.registration")
public record RegistrationProperties(boolean selfServiceEnabled) {
}

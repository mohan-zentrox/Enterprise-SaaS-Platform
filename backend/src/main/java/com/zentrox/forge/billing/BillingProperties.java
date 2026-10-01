package com.zentrox.forge.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forge.billing.*}.
 *
 * {@code webhookSecret} is the shared secret used to verify the HMAC signature on incoming webhooks
 * (see BillingWebhookController). When it is blank, webhook handling is refused outright rather
 * than accepted unverified - an unauthenticated endpoint that mutates subscription state is exactly
 * the thing an attacker would use to grant themselves an ENTERPRISE plan.
 *
 * {@code selfServePlanChange} controls whether a tenant can change their own plan through the API
 * without going through a payment provider. True is useful for development and for
 * invoice-billed enterprise deployments; false is correct anywhere a card is involved, because the
 * provider - not us - decides what a tenant has paid for.
 */
@ConfigurationProperties(prefix = "forge.billing")
public record BillingProperties(String provider, String webhookSecret, boolean selfServePlanChange) {

    public BillingProperties {
        provider = (provider == null || provider.isBlank()) ? "manual" : provider;
        webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    public boolean webhooksConfigured() {
        return !webhookSecret.isBlank();
    }
}

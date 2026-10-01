package com.zentrox.forge.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forge.mail.*}. Disabled by default so a fresh checkout runs without mail credentials -
 * see LoggingEmailSender for what happens instead.
 */
@ConfigurationProperties(prefix = "forge.mail")
public record MailProperties(boolean enabled, String from) {

    public MailProperties {
        from = (from == null || from.isBlank()) ? "no-reply@localhost" : from;
    }
}

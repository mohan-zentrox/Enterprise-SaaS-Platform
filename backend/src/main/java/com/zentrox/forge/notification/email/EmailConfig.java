package com.zentrox.forge.notification.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the email transport from {@code forge.mail.enabled}.
 *
 * Both beans are selected by {@link ConditionalOnProperty} rather than by
 * {@code @ConditionalOnMissingBean}: the latter depends on bean-definition ordering, which is only
 * well-defined inside auto-configuration, and would silently give the wrong transport if these were
 * component-scanned. Two mutually exclusive property conditions cannot be ambiguous - exactly one
 * matches, always.
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "forge.mail", name = "enabled", havingValue = "true")
    public EmailSender smtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
        return new SmtpEmailSender(mailSender, properties.from());
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}

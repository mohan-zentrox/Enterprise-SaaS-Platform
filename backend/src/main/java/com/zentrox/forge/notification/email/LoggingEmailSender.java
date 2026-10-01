package com.zentrox.forge.notification.email;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link EmailSender}: writes the message to the log instead of delivering it.
 *
 * This exists so the platform is fully runnable and testable with no mail credentials configured.
 * It is NOT a silent no-op - it logs at WARN with the recipient and subject, so a deployment that
 * has forgotten to configure a real provider is visible in the logs rather than quietly dropping
 * invitations and password resets.
 *
 * Selected by {@link EmailConfig} whenever {@code forge.mail.enabled} is not true.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.warn("""
                EMAIL NOT DELIVERED - no mail provider configured (set forge.mail.enabled=true).
                  to:      {}
                  subject: {}
                  body:    {}""", message.to(), message.subject(), message.body());
    }

    @Override
    public String describe() {
        return "logging (no mail provider configured)";
    }
}

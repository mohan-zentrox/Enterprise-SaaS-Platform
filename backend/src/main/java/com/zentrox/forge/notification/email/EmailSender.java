package com.zentrox.forge.notification.email;

/**
 * Transport abstraction for outbound email (FRD-10.2).
 *
 * Deliberately one method and no provider types in the signature. Swapping SES, SendGrid, SMTP or
 * a test double is then a bean replacement with no change to any caller - which matters because the
 * choice of provider is a deployment decision, not an application one.
 *
 * Implementations must not throw for ordinary delivery failure: a notification that cannot be
 * emailed must never fail the business operation that triggered it. See LoggingEmailSender and
 * NotificationService#notifyByEmail.
 */
public interface EmailSender {

    void send(EmailMessage message);

    /** Identifies the active transport in logs and in the health/info endpoint. */
    String describe();
}

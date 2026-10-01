package com.zentrox.forge.notification.email;

/**
 * A rendered email, ready to send. Rendering happens before this record exists so that
 * {@link EmailSender} implementations never need to know about templates - they only transport.
 */
public record EmailMessage(String to, String subject, String body) {
}

package com.zentrox.forge.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real SMTP transport, active only when {@code forge.mail.enabled=true} and Spring Boot has
 * auto-configured a {@link JavaMailSender} from {@code spring.mail.*}. Works against any SMTP
 * relay, which covers SES, SendGrid and Postmark as well as a plain mail server - so choosing a
 * provider is configuration, not code.
 *
 * Delivery failure is logged and swallowed, never rethrown: the alternative is a failed invitation
 * email rolling back the user creation that triggered it. Durable retry belongs in an outbox table
 * (see docs/ARCHITECTURE.md hardening backlog), not in a catch block here.
 */
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
            log.debug("Sent email to {} - {}", message.to(), message.subject());
        } catch (MailException e) {
            log.error("Failed to send email to {} ({}): {}", message.to(), message.subject(), e.getMessage());
        }
    }

    @Override
    public String describe() {
        return "smtp from " + fromAddress;
    }
}

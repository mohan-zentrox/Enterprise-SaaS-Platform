package com.zentrox.forge.billing;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * FRD-9.4 - the payment provider's webhook receiver.
 *
 * Mounted under {@code /v1/webhooks/**}, which SecurityConfig permits without authentication: the
 * provider has no Forge credentials. Authentication is therefore the HMAC signature, verified here
 * before anything is acted on.
 *
 * <p>The body is taken as a {@code String} and hashed as raw UTF-8 bytes rather than bound to a
 * DTO. Signatures cover the exact bytes sent; letting Jackson parse and re-serialise would reorder
 * keys and drop whitespace, and every signature would fail.
 *
 * <p><b>Response codes are chosen for the provider's retry logic, not for a human.</b> Providers
 * retry on non-2xx. So a duplicate, an unknown subscription and an unhandled event type all return
 * 200 - they are final outcomes, and retrying cannot improve them. Only a bad signature (401) and a
 * genuine server fault (500) are non-2xx, because those are worth retrying or alerting on.
 */
@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
public class BillingWebhookController {

    private static final String SIGNATURE_HEADER = "X-Forge-Signature";

    private final BillingWebhookService webhookService;

    @PostMapping("/billing")
    public ResponseEntity<Map<String, String>> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            HttpServletRequest request) {

        if (!webhookService.webhooksConfigured()) {
            // Refuse rather than accept unverified. 503 (not 401) because the fault is ours: the
            // deployment has not configured forge.billing.webhook-secret.
            log.error("Billing webhook received but forge.billing.webhook-secret is not configured; refusing");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Webhook handling is not configured"));
        }

        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty body"));
        }

        if (!webhookService.verifySignature(rawBody.getBytes(StandardCharsets.UTF_8), signature)) {
            log.warn("Rejected billing webhook with invalid signature from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }

        BillingWebhookService.Outcome outcome = webhookService.handle(rawBody);
        return ResponseEntity.ok(Map.of("outcome", outcome.name()));
    }
}

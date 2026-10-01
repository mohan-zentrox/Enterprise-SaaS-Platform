package com.zentrox.forge.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.repository.SubscriptionLookupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * FRD-9.2 / FRD-9.4 - receives subscription lifecycle events from the payment provider.
 *
 * This is the only unauthenticated endpoint in the platform that mutates billing state, so it gets
 * three defences rather than one:
 *
 * <ol>
 *   <li><b>HMAC signature over the raw body.</b> Verified with {@link MessageDigest#isEqual} for a
 *       constant-time comparison - a plain {@code equals} on the hex string leaks, through timing,
 *       how many leading bytes an attacker guessed correctly. The raw bytes are signed, not a
 *       re-serialised object, because re-serialising changes key order and whitespace and would
 *       break every signature.</li>
 *   <li><b>A configured secret is mandatory.</b> With no secret set, events are refused instead of
 *       trusted. The alternative - accepting unverified events - lets anyone who can reach the
 *       endpoint move any tenant to ENTERPRISE.</li>
 *   <li><b>Idempotency by (provider, event_id).</b> Providers deliver at least once, so duplicates
 *       are certain. Recording the id first and treating a unique violation as "already handled"
 *       makes replay a no-op.</li>
 * </ol>
 *
 * Provider-agnostic by design: it reads a small, documented JSON shape rather than embedding a
 * vendor SDK, so pointing a different provider at it is a mapping change in their dashboard (or a
 * thin translation layer), not a dependency swap.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingWebhookService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final BillingProperties billingProperties;
    private final WebhookEventLedger eventLedger;
    private final SubscriptionLookupRepository subscriptionLookupRepository;
    private final ObjectMapper objectMapper;

    /** Outcome of handling an event, so the controller can answer the provider appropriately. */
    public enum Outcome { PROCESSED, DUPLICATE_IGNORED, UNKNOWN_EVENT_TYPE, UNKNOWN_SUBSCRIPTION }

    public boolean webhooksConfigured() {
        return billingProperties.webhooksConfigured();
    }

    /**
     * @param rawBody   the exact bytes received - signatures are over these, not over a re-encoding
     * @param signature value of the provider's signature header, hex-encoded HMAC-SHA256
     */
    public boolean verifySignature(byte[] rawBody, String signature) {
        if (!billingProperties.webhooksConfigured() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    billingProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            // Constant-time: never compare signatures with String.equals.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            log.error("Cannot verify webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Applies a verified event. Signature verification happens in the controller, before this is
     * called - this method assumes the payload is authentic and concerns itself with idempotency
     * and state transition only.
     */
    @Transactional
    public Outcome handle(String rawBody) {
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Discarding unparseable billing webhook: {}", e.getMessage());
            return Outcome.UNKNOWN_EVENT_TYPE;
        }

        String eventId = text(event, "id");
        String eventType = text(event, "type");
        if (eventId == null || eventType == null) {
            log.warn("Discarding billing webhook with no id/type");
            return Outcome.UNKNOWN_EVENT_TYPE;
        }

        // Claim the event id before acting on it. The claim runs in its own transaction
        // (WebhookEventLedger), and the duplicate is caught HERE rather than inside that method -
        // a constraint violation dooms its transaction, so the catch must sit outside the
        // transaction boundary or the commit throws UnexpectedRollbackException anyway.
        try {
            eventLedger.claim(billingProperties.provider(), eventId, eventType, rawBody);
        } catch (DataIntegrityViolationException alreadyClaimed) {
            log.debug("Billing webhook {} already processed; ignoring replay", eventId);
            return Outcome.DUPLICATE_IGNORED;
        }

        JsonNode data = event.path("data");
        String providerSubscriptionId = text(data, "subscription_id");
        if (providerSubscriptionId == null) {
            log.warn("Billing webhook {} ({}) carries no data.subscription_id", eventId, eventType);
            return Outcome.UNKNOWN_SUBSCRIPTION;
        }

        Optional<Subscription> found =
                subscriptionLookupRepository.findByProviderSubscriptionId(providerSubscriptionId);
        if (found.isEmpty()) {
            // Not an error: it may belong to another environment sharing the provider account.
            log.warn("Billing webhook {} references unknown subscription {}", eventId, providerSubscriptionId);
            return Outcome.UNKNOWN_SUBSCRIPTION;
        }

        Subscription subscription = found.get();
        boolean applied = switch (eventType) {
            case "subscription.created", "subscription.updated" -> applyUpdate(subscription, data);
            case "subscription.canceled" -> {
                subscription.setStatus(SubscriptionStatus.CANCELED);
                // Downgrade capacity to FREE, but never delete anything: a cancelled tenant must be
                // able to read and export their data, and to resubscribe without having lost it.
                subscription.setPlan(SubscriptionPlan.FREE);
                yield true;
            }
            case "payment.failed" -> {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                yield true;
            }
            case "payment.succeeded" -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                yield true;
            }
            default -> false;
        };

        if (!applied) {
            log.debug("Billing webhook {} has unhandled type {}", eventId, eventType);
            return Outcome.UNKNOWN_EVENT_TYPE;
        }

        subscriptionLookupRepository.save(subscription);
        log.info("Applied billing event {} ({}) to tenant {}", eventId, eventType, subscription.getTenantId());
        return Outcome.PROCESSED;
    }

    private boolean applyUpdate(Subscription subscription, JsonNode data) {
        String plan = text(data, "plan");
        if (plan != null) {
            try {
                subscription.setPlan(SubscriptionPlan.valueOf(plan.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // An unrecognised plan name must not silently become FREE (a downgrade nobody asked
                // for) nor ENTERPRISE (capacity nobody paid for). Keep the current plan and shout.
                log.error("Billing webhook names unknown plan '{}' for tenant {}; plan left unchanged",
                        plan, subscription.getTenantId());
            }
        }
        String status = text(data, "status");
        if (status != null) {
            try {
                subscription.setStatus(SubscriptionStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.error("Billing webhook names unknown status '{}' for tenant {}; status left unchanged",
                        status, subscription.getTenantId());
            }
        }
        JsonNode periodEnd = data.get("current_period_end");
        if (periodEnd != null && periodEnd.isNumber()) {
            subscription.setCurrentPeriodEnd(Instant.ofEpochSecond(periodEnd.asLong()));
        }
        JsonNode cancelAtPeriodEnd = data.get("cancel_at_period_end");
        if (cancelAtPeriodEnd != null && cancelAtPeriodEnd.isBoolean()) {
            subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd.asBoolean());
        }
        return true;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}

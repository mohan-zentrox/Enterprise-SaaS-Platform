package com.zentrox.forge.billing;

import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook receiver is the only unauthenticated write path into billing state, so these tests
 * are about the security properties as much as the state machine: a forged signature must be
 * rejected, and a replayed event must not be applied twice.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "forge.billing.provider=test-provider",
        "forge.billing.webhook-secret=test-webhook-secret-do-not-use-in-prod"
})
@Transactional
class BillingWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret-do-not-use-in-prod";

    @Autowired
    private BillingWebhookService webhookService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Tenant tenant;

    /**
     * Event ids are made unique per test method because the idempotency ledger commits in its OWN
     * transaction (WebhookEventLedger uses REQUIRES_NEW) and therefore survives this test class's
     * rollback - which is exactly the behaviour production needs, but it means a reused id would
     * collide with a previous test's row and make the suite order-dependent.
     */
    private String idPrefix;
    private String subscriptionId;

    @BeforeEach
    void setUp() {
        idPrefix = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_";
        // The provider subscription id must also be unique per test: it carries a partial unique
        // index, and rows from a rolled-back test are gone but the ledger rows are not.
        subscriptionId = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tenant = tenantRepository.save(Tenant.builder()
                .name("webhook-tenant").slug("webhook-" + subscriptionId).status(TenantStatus.ACTIVE).build());
        subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId())
                .plan(SubscriptionPlan.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .provider("test-provider")
                .providerSubscriptionId(subscriptionId)
                .build());
    }

    // ------------------------------------------------------------------ signature verification

    @Test
    void acceptsACorrectlySignedBody() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"data\":{}}";
        assertThat(webhookService.verifySignature(body.getBytes(StandardCharsets.UTF_8), sign(body))).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        String original = "{\"id\":\"evt_1\",\"type\":\"subscription.updated\",\"data\":{\"plan\":\"STARTER\"}}";
        String signature = sign(original);
        String tampered = original.replace("STARTER", "ENTERPRISE");

        // The exact attack this defends against: upgrading yourself by editing a replayed payload.
        assertThat(webhookService.verifySignature(tampered.getBytes(StandardCharsets.UTF_8), signature)).isFalse();
    }

    @Test
    void rejectsAMissingOrBlankSignature() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"data\":{}}";
        assertThat(webhookService.verifySignature(body.getBytes(StandardCharsets.UTF_8), null)).isFalse();
        assertThat(webhookService.verifySignature(body.getBytes(StandardCharsets.UTF_8), "   ")).isFalse();
    }

    @Test
    void rejectsASignatureMadeWithTheWrongSecret() {
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"data\":{}}";
        assertThat(webhookService.verifySignature(
                body.getBytes(StandardCharsets.UTF_8), sign(body, "a-different-secret"))).isFalse();
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void appliesAnEventOnceAndIgnoresTheReplay() {
        String body = event("dup", "subscription.updated", "\"plan\":\"PROFESSIONAL\"");

        assertThat(webhookService.handle(body)).isEqualTo(BillingWebhookService.Outcome.PROCESSED);
        assertThat(currentPlan()).isEqualTo(SubscriptionPlan.PROFESSIONAL);

        // Same event id arriving again - providers guarantee at-least-once delivery.
        assertThat(webhookService.handle(body)).isEqualTo(BillingWebhookService.Outcome.DUPLICATE_IGNORED);
    }

    // ------------------------------------------------------------------ state transitions

    @Test
    void upgradesThePlanOnSubscriptionUpdated() {
        webhookService.handle(event("up", "subscription.updated", "\"plan\":\"STARTER\""));
        assertThat(currentPlan()).isEqualTo(SubscriptionPlan.STARTER);
    }

    @Test
    void marksPastDueOnPaymentFailure() {
        webhookService.handle(event("fail", "payment.failed", null));
        assertThat(currentStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void recoversToActiveOnPaymentSuccess() {
        webhookService.handle(event("fail", "payment.failed", null));
        webhookService.handle(event("ok", "payment.succeeded", null));
        assertThat(currentStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void cancellationDowngradesCapacityButKeepsTheTenant() {
        webhookService.handle(event("up", "subscription.updated", "\"plan\":\"PROFESSIONAL\""));
        webhookService.handle(event("cancel", "subscription.canceled", null));

        assertThat(currentStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(currentPlan()).isEqualTo(SubscriptionPlan.FREE);
        // The tenant itself must survive - a cancelled customer still needs to read and export data.
        assertThat(tenantRepository.findById(tenant.getId())).isPresent();
    }

    @Test
    void anUnknownPlanNameLeavesThePlanUnchangedRatherThanGuessing() {
        webhookService.handle(event("up", "subscription.updated", "\"plan\":\"PROFESSIONAL\""));
        webhookService.handle(event("bogus", "subscription.updated", "\"plan\":\"PLATINUM_DELUXE\""));

        // Neither silently downgraded to FREE nor upgraded - both would be wrong.
        assertThat(currentPlan()).isEqualTo(SubscriptionPlan.PROFESSIONAL);
    }

    @Test
    void reportsUnknownSubscriptionWithoutFailing() {
        String body = """
                {"id":"%sother","type":"payment.failed","data":{"subscription_id":"sub_someone_else"}}"""
                .formatted(idPrefix);
        assertThat(webhookService.handle(body)).isEqualTo(BillingWebhookService.Outcome.UNKNOWN_SUBSCRIPTION);
    }

    @Test
    void reportsUnknownEventTypeWithoutFailing() {
        assertThat(webhookService.handle(event("weird", "invoice.doodled", null)))
                .isEqualTo(BillingWebhookService.Outcome.UNKNOWN_EVENT_TYPE);
    }

    @Test
    void discardsUnparseableJsonWithoutThrowing() {
        assertThat(webhookService.handle("not json at all"))
                .isEqualTo(BillingWebhookService.Outcome.UNKNOWN_EVENT_TYPE);
    }

    // ------------------------------------------------------------------ helpers

    private String event(String id, String type, String extraData) {
        String data = "\"subscription_id\":\"" + subscriptionId + "\""
                + (extraData == null ? "" : "," + extraData);
        return "{\"id\":\"%s\",\"type\":\"%s\",\"data\":{%s}}".formatted(idPrefix + id, type, data);
    }

    private SubscriptionPlan currentPlan() {
        return subscriptionRepository.findByTenantId(tenant.getId()).orElseThrow().getPlan();
    }

    private SubscriptionStatus currentStatus() {
        return subscriptionRepository.findByTenantId(tenant.getId()).orElseThrow().getStatus();
    }

    private String sign(String body) {
        return sign(body, SECRET);
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

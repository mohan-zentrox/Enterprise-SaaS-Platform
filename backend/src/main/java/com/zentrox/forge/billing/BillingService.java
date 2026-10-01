package com.zentrox.forge.billing;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.billing.dto.SubscriptionResponse;
import com.zentrox.forge.billing.dto.UsageResponse;
import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.repository.tenant.SubscriptionRepository;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * FRD Section 9 - tenant-facing subscription reads and plan changes.
 *
 * State that the payment provider owns (status, period end, provider ids) is only ever written by
 * {@link BillingWebhookService}. This class writes the plan directly only when
 * {@code forge.billing.self-serve-plan-change} is enabled, which is for development and
 * invoice-billed deployments. With a card provider in the loop, letting the application decide a
 * tenant is now on ENTERPRISE would mean granting capacity nobody has paid for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementService entitlementService;
    private final BillingProperties billingProperties;

    public SubscriptionResponse currentSubscription() {
        UUID tenantId = TenantContext.requireTenantId();
        Subscription subscription = entitlementService.currentSubscription(tenantId);
        return SubscriptionResponse.from(subscription, usageFor(tenantId, subscription.getPlan()));
    }

    /** Current usage against the plan's limits - what a billing page needs to render. */
    public List<UsageResponse> usageFor(UUID tenantId, SubscriptionPlan plan) {
        return Arrays.stream(UsageMetric.values())
                .map(metric -> UsageResponse.of(
                        metric,
                        entitlementService.currentUsage(tenantId, metric),
                        plan.limitFor(metric)))
                .toList();
    }

    /**
     * Changes the plan without involving a payment provider.
     *
     * Refuses to downgrade below current usage. Allowing it would leave a tenant permanently over
     * their limit with every write rejected and no way back except deleting data - a state the API
     * should not be able to put anyone into. Upgrades are always allowed.
     */
    @Audited(action = "SUBSCRIPTION_PLAN_CHANGE", entityType = "Subscription")
    @Transactional
    public SubscriptionResponse changePlan(SubscriptionPlan newPlan) {
        if (!billingProperties.selfServePlanChange()) {
            throw new ConflictException(
                    "Plan changes are managed by the payment provider for this deployment. "
                            + "Use the billing portal rather than this endpoint.");
        }

        UUID tenantId = TenantContext.requireTenantId();
        Subscription subscription = requirePersistedSubscription(tenantId);

        for (UsageMetric metric : UsageMetric.values()) {
            long newLimit = newPlan.limitFor(metric);
            if (newLimit == SubscriptionPlan.UNLIMITED) {
                continue;
            }
            long used = entitlementService.currentUsage(tenantId, metric);
            if (used > newLimit) {
                throw new ConflictException(
                        "Cannot move to %s: it allows %d %s and you are using %d. Reduce usage first."
                                .formatted(newPlan.name(), newLimit,
                                        metric.name().toLowerCase().replace('_', ' '), used));
            }
        }

        subscription.setPlan(newPlan);
        return SubscriptionResponse.from(
                subscriptionRepository.save(subscription), usageFor(tenantId, newPlan));
    }

    /**
     * Unlike {@link EntitlementService#currentSubscription}, which tolerates a missing row by
     * returning a transient FREE default, a write needs a real persisted row - saving the transient
     * one would silently create a second subscription for the tenant.
     */
    private Subscription requirePersistedSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> subscriptionRepository.save(Subscription.builder()
                        .tenantId(tenantId)
                        .plan(SubscriptionPlan.FREE)
                        .status(SubscriptionStatus.ACTIVE)
                        .build()));
    }
}

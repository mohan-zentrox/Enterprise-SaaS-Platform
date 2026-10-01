package com.zentrox.forge.billing;

import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.entity.TenantUsage;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.EntitlementExceededException;
import com.zentrox.forge.repository.TenantUsageRepository;
import com.zentrox.forge.repository.tenant.ApiKeyRepository;
import com.zentrox.forge.repository.tenant.SubscriptionRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.repository.tenant.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FRD Section 9 - enforcement, at the point of use.
 *
 * Two kinds of metric, handled differently on purpose:
 *
 * <ul>
 *   <li><b>Counted live</b> (seats, workflow definitions, API keys). The truth is a {@code COUNT}
 *       over the table that already exists. Maintaining a separate counter for these would create a
 *       second source of truth that drifts the first time a row is deleted by anything that forgot
 *       to decrement.</li>
 *   <li><b>Metered</b> (instances per month). A rate, not a population - there is nothing to count,
 *       so it is incremented in {@code tenant_usage} and reset per period.</li>
 * </ul>
 *
 * A tenant with no subscription row is treated as FREE rather than as unentitled. Failing closed
 * sounds safer but means one missing row locks a paying customer out of their own data; the
 * migration backfills every tenant, so a missing row is a bug to log, not a reason to deny service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ApiKeyRepository apiKeyRepository;

    public SubscriptionPlan currentPlan(UUID tenantId) {
        return currentSubscription(tenantId).getPlan();
    }

    public Subscription currentSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    log.warn("Tenant {} has no subscription row; defaulting to FREE. "
                            + "V4 should have backfilled this - investigate.", tenantId);
                    return Subscription.builder()
                            .tenantId(tenantId)
                            .plan(SubscriptionPlan.FREE)
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                });
    }

    /** Current consumption of a metric, whichever way it is tracked. */
    public long currentUsage(UUID tenantId, UsageMetric metric) {
        return switch (metric) {
            case SEATS -> userRepository.countByTenantIdAndStatus(tenantId, UserStatus.ACTIVE);
            case WORKFLOW_DEFINITIONS -> workflowDefinitionRepository.countByTenantId(tenantId);
            // Revoked keys do not consume capacity, so this is an active count.
            case API_KEYS -> apiKeyRepository.countByTenantIdAndRevokedAtIsNull(tenantId);
            case WORKFLOW_INSTANCES_PER_MONTH ->
                    usageRepository.findByTenantIdAndMetric(tenantId, metric)
                            .map(TenantUsage::getUsed)
                            .orElse(0L);
        };
    }

    public long limit(UUID tenantId, UsageMetric metric) {
        return currentPlan(tenantId).limitFor(metric);
    }

    /** Non-throwing form, for read-only "can they?" questions such as rendering a UI. */
    public boolean isEntitled(UUID tenantId, UsageMetric metric) {
        Subscription subscription = currentSubscription(tenantId);
        if (!subscription.getStatus().permitsWrites()) {
            return false;
        }
        long limit = subscription.getPlan().limitFor(metric);
        return limit == SubscriptionPlan.UNLIMITED || currentUsage(tenantId, metric) < limit;
    }

    /**
     * Throwing form, called by {@link com.zentrox.forge.billing.RequiresEntitlement} before a write.
     *
     * @throws EntitlementExceededException mapped to 402 Payment Required
     */
    public void requireEntitlement(UUID tenantId, UsageMetric metric) {
        Subscription subscription = currentSubscription(tenantId);

        if (!subscription.getStatus().permitsWrites()) {
            throw new EntitlementExceededException(
                    "Your subscription is %s. Update your billing details to continue making changes; "
                            .formatted(subscription.getStatus().name().toLowerCase().replace('_', ' '))
                            + "your existing data remains readable.");
        }

        long limit = subscription.getPlan().limitFor(metric);
        if (limit == SubscriptionPlan.UNLIMITED) {
            return;
        }

        long used = currentUsage(tenantId, metric);
        if (used >= limit) {
            throw new EntitlementExceededException(
                    "Your %s plan allows %d %s and you are using %d. Upgrade your plan to add more."
                            .formatted(subscription.getPlan().name(), limit,
                                    metric.name().toLowerCase().replace('_', ' '), used));
        }
    }

    /**
     * Records consumption of a metered metric.
     *
     * <p><b>Joins the caller's transaction (default {@code REQUIRED}) - deliberately NOT
     * {@code REQUIRES_NEW}.</b> This was originally REQUIRES_NEW, reasoning that usage already served
     * should be billed even if the request later failed. That reasoning was wrong twice over:
     *
     * <ol>
     *   <li><b>It billed for work that did not happen.</b> If the caller's transaction rolls back,
     *       the workflow instance was never created - so there is nothing to charge for. Metering it
     *       anyway overcounts against a paying customer's limit.</li>
     *   <li><b>It could deadlock against its own caller.</b> A new transaction cannot see, and must
     *       wait for, locks held by the suspended outer one. Any caller holding a lock on this
     *       tenant's {@code tenant_usage} row - the monthly reset job does exactly that - would wait
     *       for a transaction that cannot proceed until the caller finishes. H2 surfaced this as
     *       "Timeout trying to lock table TENANT_USAGE ... locked by tx 1 ... by tx 2"; Postgres
     *       would block until a lock timeout.</li>
     * </ol>
     *
     * <p>The consequence to be aware of: a failure here now fails the caller's request rather than
     * being absorbed. That is the correct trade - if usage cannot be recorded, the write should not
     * silently proceed unmetered.
     *
     * <p>The upsert is written as "increment, and insert only if nothing was incremented" so
     * concurrent callers cannot both insert the same key.
     */
    @Transactional
    public void recordUsage(UUID tenantId, UsageMetric metric, long delta) {
        Instant now = Instant.now();
        int updated = usageRepository.incrementExisting(tenantId, metric, delta, now);
        if (updated == 0) {
            try {
                usageRepository.save(TenantUsage.builder()
                        .tenantId(tenantId)
                        .metric(metric)
                        .used(Math.max(delta, 0))
                        .build());
            } catch (RuntimeException e) {
                // Lost the insert race with a concurrent first-use; the row exists now, so retry
                // the increment rather than failing the request over a counter.
                log.debug("Usage row for {}/{} created concurrently, retrying increment", tenantId, metric);
                usageRepository.incrementExisting(tenantId, metric, delta, now);
            }
        }
    }
}

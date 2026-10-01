package com.zentrox.forge.billing.dto;

import com.zentrox.forge.billing.SubscriptionPlan;
import com.zentrox.forge.billing.SubscriptionStatus;
import com.zentrox.forge.entity.Subscription;

import java.time.Instant;
import java.util.List;

/**
 * Note there is no provider customer/subscription id here. Those are internal billing plumbing;
 * exposing them on a tenant-facing endpoint invites clients to couple to the payment provider.
 */
public record SubscriptionResponse(SubscriptionPlan plan, SubscriptionStatus status,
                                    Instant currentPeriodEnd, boolean cancelAtPeriodEnd,
                                    List<UsageResponse> usage) {

    public static SubscriptionResponse from(Subscription s, List<UsageResponse> usage) {
        return new SubscriptionResponse(s.getPlan(), s.getStatus(), s.getCurrentPeriodEnd(),
                s.isCancelAtPeriodEnd(), usage);
    }
}

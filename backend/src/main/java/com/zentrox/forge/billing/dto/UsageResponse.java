package com.zentrox.forge.billing.dto;

import com.zentrox.forge.billing.SubscriptionPlan;
import com.zentrox.forge.billing.UsageMetric;

/** {@code limit} is null when the plan grants unlimited use of the metric. */
public record UsageResponse(UsageMetric metric, long used, Long limit, boolean unlimited) {

    public static UsageResponse of(UsageMetric metric, long used, long limit) {
        boolean unlimited = limit == SubscriptionPlan.UNLIMITED;
        return new UsageResponse(metric, used, unlimited ? null : limit, unlimited);
    }
}

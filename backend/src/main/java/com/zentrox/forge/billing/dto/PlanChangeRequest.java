package com.zentrox.forge.billing.dto;

import com.zentrox.forge.billing.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public record PlanChangeRequest(@NotNull SubscriptionPlan plan) {
}

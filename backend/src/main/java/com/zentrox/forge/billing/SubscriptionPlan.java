package com.zentrox.forge.billing;

/**
 * SCAFFOLD ONLY - FRD Section 9 (Subscription & Billing).
 *
 * TODO(FRD-9.1): Replace with the real plan catalog (pricing tiers, seat limits,
 *   feature flags per plan) once billing requirements are finalized. This enum exists
 *   so {@link EntitlementService} and {@code Tenant} can reference a plan concept without
 *   blocking the rest of the foundation on billing scope.
 */
public enum SubscriptionPlan {
    FREE,
    STARTER,
    PROFESSIONAL,
    ENTERPRISE
}

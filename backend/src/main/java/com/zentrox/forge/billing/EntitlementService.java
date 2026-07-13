package com.zentrox.forge.billing;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SCAFFOLD ONLY - FRD Section 9 (Subscription & Billing / Entitlement Enforcement).
 *
 * TODO(FRD-9.2): Wire this up to the payment provider webhook (Stripe or equivalent -
 *   see FRD-9.4 "Payment Provider Integration") to keep tenant entitlements in sync
 *   with subscription status.
 * TODO(FRD-9.3): Enforce entitlements at the point of use - e.g. a
 *   {@code @RequiresEntitlement("WORKFLOW_DEFINITION_CREATE")} method interceptor
 *   (mirroring the AuditAspect pattern in com.zentrox.forge.aop) that checks seat/usage
 *   limits before allowing a write, and returns 402 Payment Required when exceeded.
 * TODO(FRD-9.5): Persist entitlement/usage counters - likely a `tenant_entitlements`
 *   table plus Redis counters for high-frequency usage metering.
 */
@Service
public class EntitlementService {

    public boolean isEntitled(UUID tenantId, String featureKey) {
        // TODO(FRD-9.3): implement real plan/feature lookup instead of allow-all.
        throw new UnsupportedOperationException(
                "Entitlement enforcement is not implemented yet - see FRD Section 9");
    }

    public SubscriptionPlan currentPlan(UUID tenantId) {
        // TODO(FRD-9.1): read from a persisted subscription record.
        throw new UnsupportedOperationException("Billing/subscription lookup is not implemented yet - see FRD Section 9");
    }
}

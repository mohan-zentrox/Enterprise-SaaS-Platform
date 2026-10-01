package com.zentrox.forge.billing;

/**
 * Mirrors the states every payment provider models, kept deliberately small.
 *
 * {@link #PAST_DUE} is not the same as {@link #CANCELED}: a failed payment should degrade the
 * service (block new writes) while leaving the tenant's data readable so they can fix their card.
 * Treating the two identically is how a billing hiccup turns into a data-loss incident.
 */
public enum SubscriptionStatus {
    ACTIVE,
    TRIALING,
    PAST_DUE,
    CANCELED;

    /** Whether a tenant in this state may perform capacity-consuming writes. */
    public boolean permitsWrites() {
        return this == ACTIVE || this == TRIALING;
    }
}

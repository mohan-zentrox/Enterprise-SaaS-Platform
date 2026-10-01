package com.zentrox.forge.billing;

/**
 * The things a plan can cap. Separate from {@code Permission}: a permission answers "is this caller
 * allowed to do it", an entitlement answers "has this tenant paid for the capacity to do it". Both
 * must pass, and conflating them would mean an owner of a free plan either bypasses limits or loses
 * authority they legitimately hold.
 */
public enum UsageMetric {

    /** Active users. Counted live from the users table rather than incremented. */
    SEATS,

    /** Workflow definitions in existence. Counted live. */
    WORKFLOW_DEFINITIONS,

    /** Instances started in the current billing period. Incremented, since it is a rate not a count. */
    WORKFLOW_INSTANCES_PER_MONTH,

    /** Active (non-revoked) API keys. Counted live. */
    API_KEYS
}

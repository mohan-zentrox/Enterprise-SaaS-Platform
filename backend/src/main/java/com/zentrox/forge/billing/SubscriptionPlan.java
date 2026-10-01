package com.zentrox.forge.billing;

import java.util.Map;

/**
 * FRD Section 9 - the plan catalog, and the single source of truth for what each plan permits.
 *
 * Limits live on the enum rather than in the database on purpose: they are product definitions that
 * ship with a release, not tenant data. Putting them in a table invites per-tenant edits that no
 * code path validates and that nobody can reproduce in a test. A genuinely bespoke enterprise
 * agreement is a new enum constant plus a migration, which is a reviewable change.
 *
 * {@link #UNLIMITED} is -1 rather than {@code Long.MAX_VALUE} so an accidental arithmetic overflow
 * cannot silently turn "unlimited" into a very small number.
 */
public enum SubscriptionPlan {

    FREE(Map.of(
            UsageMetric.SEATS, 3L,
            UsageMetric.WORKFLOW_DEFINITIONS, 2L,
            UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 100L,
            UsageMetric.API_KEYS, 0L)),

    STARTER(Map.of(
            UsageMetric.SEATS, 10L,
            UsageMetric.WORKFLOW_DEFINITIONS, 10L,
            UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 1_000L,
            UsageMetric.API_KEYS, 2L)),

    PROFESSIONAL(Map.of(
            UsageMetric.SEATS, 50L,
            UsageMetric.WORKFLOW_DEFINITIONS, 100L,
            UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 25_000L,
            UsageMetric.API_KEYS, 10L)),

    ENTERPRISE(Map.of(
            UsageMetric.SEATS, Limits.UNLIMITED,
            UsageMetric.WORKFLOW_DEFINITIONS, Limits.UNLIMITED,
            UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Limits.UNLIMITED,
            UsageMetric.API_KEYS, Limits.UNLIMITED));

    /**
     * Holder so the enum constants above can reference UNLIMITED. A static field declared directly
     * on the enum would be an illegal forward reference: enum constants are initialised before the
     * enum's own static fields, so ENTERPRISE would be reading a field that does not exist yet.
     * A nested class is initialised on first access, which is legal from a constant initialiser.
     */
    private static final class Limits {
        private static final long UNLIMITED = -1L;
    }

    public static final long UNLIMITED = Limits.UNLIMITED;

    private final Map<UsageMetric, Long> limits;

    SubscriptionPlan(Map<UsageMetric, Long> limits) {
        this.limits = limits;
    }

    /**
     * The limit for a metric. An unlisted metric is treated as {@link #UNLIMITED}: adding a new
     * metric should not retroactively forbid it on every existing plan, which is what defaulting to
     * zero would do.
     */
    public long limitFor(UsageMetric metric) {
        return limits.getOrDefault(metric, UNLIMITED);
    }

    public boolean isUnlimited(UsageMetric metric) {
        return limitFor(metric) == UNLIMITED;
    }
}

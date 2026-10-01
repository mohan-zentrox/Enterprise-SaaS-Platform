package com.zentrox.forge.dashboard;

/**
 * The widgets a dashboard can hold (FRD-11.2).
 *
 * A closed enum rather than a free-text type on purpose: every widget's data is produced by
 * server-side code reading through the tenant-scoped repositories, so a type nothing can render is
 * not a configuration option - it is a broken dashboard. Adding a widget is a code change plus an
 * enum constant, which is exactly the reviewable unit it should be.
 */
public enum WidgetType {

    /** Count of workflow definitions in the tenant. */
    WORKFLOW_DEFINITION_COUNT,

    /** Count of workflow instances in the tenant. */
    WORKFLOW_INSTANCE_COUNT,

    /** Instances grouped by their current state. */
    INSTANCES_BY_STATE,

    /** Count of active users. */
    ACTIVE_USER_COUNT,

    /** Most recent audit entries. */
    RECENT_AUDIT_ACTIVITY,

    /** Plan, status and usage against limits. */
    SUBSCRIPTION_USAGE
}

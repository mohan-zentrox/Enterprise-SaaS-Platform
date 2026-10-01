package com.zentrox.forge.billing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as consuming plan capacity (FRD-9.3).
 *
 * Checked by {@link EntitlementAspect} before the method runs, mirroring the {@code @Audited} /
 * AuditAspect pattern already used for audit logging - so enforcement is declarative and cannot be
 * forgotten halfway through a method body.
 *
 * Sits alongside {@code @PreAuthorize}, never instead of it: authorization asks whether this caller
 * may act, entitlement asks whether this tenant has capacity. Both have to pass.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresEntitlement {

    /** The capacity this method consumes. */
    UsageMetric value();

    /**
     * Whether to meter the usage after a successful call. True only for rate-style metrics
     * (instances per month); population metrics are counted live and must not also be incremented,
     * or they would be double-counted.
     */
    boolean meter() default false;
}

package com.zentrox.forge.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion should append an
 * {@link com.zentrox.forge.entity.AuditLog} row. See {@link AuditAspect} for how
 * {@code action}/{@code entityType}/entityId are resolved.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** Short, stable action code, e.g. "WORKFLOW_DEFINITION_CREATE". */
    String action();

    /** Entity type this action applies to, e.g. "WorkflowDefinition". */
    String entityType();
}

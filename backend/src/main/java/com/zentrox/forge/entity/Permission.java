package com.zentrox.forge.entity;

/**
 * Permission catalog for RBAC. Every write endpoint is guarded by a
 * {@code @PreAuthorize("hasAuthority('...')")} check against one of these values.
 * Access tokens embed the caller's resolved permission set as JWT claims so
 * authorization checks stay stateless (no DB hit per request).
 */
public enum Permission {
    TENANT_READ,
    TENANT_UPDATE,
    TENANT_SUSPEND,

    USER_INVITE,
    USER_READ,
    USER_UPDATE,
    USER_DEACTIVATE,

    ROLE_MANAGE,

    WORKFLOW_DEFINITION_CREATE,
    WORKFLOW_DEFINITION_READ,
    WORKFLOW_DEFINITION_UPDATE,
    WORKFLOW_DEFINITION_DELETE,

    WORKFLOW_INSTANCE_CREATE,
    WORKFLOW_INSTANCE_READ,
    WORKFLOW_INSTANCE_TRANSITION,

    AUDIT_LOG_READ
}

package com.zentrox.forge.service;

import com.zentrox.forge.entity.Permission;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Default permission grants for the three system roles seeded on every new tenant
 * (FRD Section 4, RBAC). Tenants cannot rename or delete these three roles in this
 * foundation release; custom roles are a documented follow-up (see docs/ARCHITECTURE.md).
 */
public final class RoleCatalog {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    private RoleCatalog() {
    }

    public static Map<String, Set<Permission>> systemRoleGrants() {
        Set<Permission> ownerGrants = EnumSet.allOf(Permission.class);

        Set<Permission> adminGrants = EnumSet.allOf(Permission.class);
        adminGrants.remove(Permission.TENANT_SUSPEND);

        Set<Permission> memberGrants = EnumSet.of(
                Permission.TENANT_READ,
                Permission.USER_READ,
                Permission.WORKFLOW_DEFINITION_READ,
                Permission.WORKFLOW_INSTANCE_READ,
                Permission.WORKFLOW_INSTANCE_CREATE,
                Permission.WORKFLOW_INSTANCE_TRANSITION);

        return Map.of(OWNER, ownerGrants, ADMIN, adminGrants, MEMBER, memberGrants);
    }
}

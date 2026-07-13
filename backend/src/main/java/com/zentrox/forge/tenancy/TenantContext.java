package com.zentrox.forge.tenancy;

import java.util.UUID;

/**
 * Request-scoped (thread-local) holder for the tenant resolved by
 * {@link TenantFilterInterceptor}. Every tenant-scoped service call reads the
 * current tenant from here rather than trusting a client-supplied value picked
 * up deeper in the call stack.
 *
 * MUST be cleared at the end of every request (see TenantFilterInterceptor#afterCompletion)
 * because the thread is returned to a pool and would otherwise leak tenant context
 * into an unrelated later request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT_TENANT_ID.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant bound to the current request. This endpoint requires an authenticated "
                            + "principal with a tenant claim, or an X-Tenant-Id header on a request that legitimately "
                            + "precedes authentication.");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}

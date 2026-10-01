package com.zentrox.forge.billing;

import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces {@link RequiresEntitlement} (FRD-9.3).
 *
 * Two advices rather than an {@code @Around}:
 *
 * <ul>
 *   <li>{@code @Before} rejects an over-limit call before any work is done, so nothing partial is
 *       written and the 402 is cheap.</li>
 *   <li>{@code @AfterReturning} meters, so only work that actually succeeded is billed. Metering in
 *       the "before" advice would charge tenants for failed requests.</li>
 * </ul>
 *
 * Unlike AuditAspect, a failure here is NOT swallowed: an audit entry that fails to write is a lost
 * record, but an entitlement check that fails open is unmetered, unpaid usage. If the check cannot
 * run, the call does not proceed.
 */
@Slf4j
@Aspect
@Component
@Order(0) // Runs before the transaction advice, so a rejected call never opens a transaction.
@RequiredArgsConstructor
public class EntitlementAspect {

    private final EntitlementService entitlementService;

    @Before("@annotation(requiresEntitlement)")
    public void checkEntitlement(RequiresEntitlement requiresEntitlement) {
        entitlementService.requireEntitlement(requireTenant(), requiresEntitlement.value());
    }

    @AfterReturning(pointcut = "@annotation(requiresEntitlement)", returning = "result")
    public void meterUsage(RequiresEntitlement requiresEntitlement, Object result) {
        if (!requiresEntitlement.meter()) {
            return;
        }
        try {
            entitlementService.recordUsage(requireTenant(), requiresEntitlement.value(), 1L);
        } catch (RuntimeException e) {
            // Logged, but NOT suppressed in effect: recordUsage now joins the caller's transaction
            // (see EntitlementService#recordUsage for why REQUIRES_NEW was wrong), so a failure here
            // has already marked that transaction rollback-only and the request will fail at commit
            // regardless of this catch. That is the intended outcome - a write that could not be
            // metered should not proceed silently. The catch exists only so the cause is logged with
            // context rather than surfacing as an opaque commit failure.
            log.error("Failed to meter {} for tenant {}; the request will fail: {}",
                    requiresEntitlement.value(), TenantContext.getTenantId(), e.getMessage(), e);
        }
    }

    private UUID requireTenant() {
        return TenantContext.requireTenantId();
    }
}

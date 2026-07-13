package com.zentrox.forge.tenancy;

import com.zentrox.forge.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Resolves the tenant for the current request and binds it to {@link TenantContext}
 * for every downstream service/repository call, then unbinds it once the request
 * completes.
 *
 * Resolution order:
 *   1. The authenticated principal's tenant claim (normal case - JwtAuthenticationFilter
 *      has already populated the SecurityContext by the time this interceptor runs).
 *   2. The {@code X-Tenant-Id} header, for the handful of endpoints that legitimately run
 *      before authentication exists (e.g. login itself is scoped by tenant slug in the
 *      request body, not this header; this fallback mainly exists for public/service-to-service
 *      calls that will use API-key auth - see docs/ARCHITECTURE.md "Public API" scaffold).
 *
 * Also enables the Hibernate {@code tenantFilter} (see TenantScopedEntity subclasses) on the
 * request-bound persistence context, so even a raw JPQL/criteria query written by a future
 * contributor is filtered by tenant_id as a defense-in-depth measure on top of the
 * explicit tenant-scoped repository methods.
 */
@Slf4j
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    // Field injection is required here (not constructor injection): @PersistenceContext
    // is only honored by Spring's PersistenceAnnotationBeanPostProcessor on fields/setters,
    // and it supplies a context-aware proxy that resolves to the request-bound EntityManager
    // (this bean itself is a singleton).
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID tenantId = resolveTenantId(request);
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
            enableHibernateTenantFilter(tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 @Nullable Exception ex) {
        TenantContext.clear();
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            return principal.getTenantId();
        }

        String header = request.getHeader(TENANT_HEADER);
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring malformed {} header: {}", TENANT_HEADER, header);
            }
        }
        return null;
    }

    private void enableHibernateTenantFilter(UUID tenantId) {
        try {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        } catch (Exception e) {
            // Defense-in-depth layer only; explicit tenant-scoped repository methods remain
            // the primary enforcement mechanism, so we log and continue rather than fail the request.
            log.debug("Could not enable Hibernate tenant filter: {}", e.getMessage());
        }
    }
}

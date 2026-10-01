package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.Dashboard;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardRepository extends TenantScopedRepository<Dashboard, UUID> {

    /**
     * Everything the given user may see: their own dashboards plus the tenant's shared ones.
     *
     * Written as an explicit query rather than a derived method name. The derived form of this
     * predicate is {@code findAllByTenantIdAndOwnerUserIdOrTenantIdAndOwnerUserIdIsNull...}, which
     * is both unreadable and genuinely dangerous: Spring Data gives {@code And} higher precedence
     * than {@code Or} with no parentheses available, so a small mistake in the name silently
     * produces {@code (tenant = ? AND owner = ?) OR (owner IS NULL)} - dropping the tenant
     * predicate from the second branch and leaking every tenant's shared dashboards.
     */
    @Query("""
            SELECT d FROM Dashboard d
             WHERE d.tenantId = :tenantId
               AND (d.ownerUserId = :userId OR d.ownerUserId IS NULL)
             ORDER BY d.name ASC
            """)
    List<Dashboard> findVisibleTo(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * A dashboard the given user may read: their own, or a shared one. Used instead of
     * {@code findByIdAndTenantId} so one user cannot open another's private dashboard by id.
     */
    @Query("""
            SELECT d FROM Dashboard d
             WHERE d.id = :id
               AND d.tenantId = :tenantId
               AND (d.ownerUserId = :userId OR d.ownerUserId IS NULL)
            """)
    Optional<Dashboard> findVisibleToById(@Param("id") UUID id,
                                           @Param("tenantId") UUID tenantId,
                                           @Param("userId") UUID userId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}

package com.zentrox.forge.repository;

import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.entity.TenantUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Usage counters. Every method is tenant-qualified, so isolation holds even though TenantUsage
 * cannot extend TenantScopedEntity (composite key - see that class).
 */
public interface TenantUsageRepository extends JpaRepository<TenantUsage, TenantUsage.Key> {

    Optional<TenantUsage> findByTenantIdAndMetric(UUID tenantId, UsageMetric metric);

    /**
     * Atomic increment in the database rather than read-modify-write in Java.
     *
     * Two concurrent workflow starts would otherwise both read the same counter, both add one, and
     * both write the same value - letting a tenant exceed a paid limit under exactly the concurrent
     * load that makes limits matter. A single UPDATE ... SET used = used + :delta is serialised by
     * the row lock.
     *
     * {@code now} is a bound parameter rather than HQL's {@code CURRENT_TIMESTAMP}, which resolves to
     * a {@code java.sql.Timestamp} and cannot be assigned to an {@code Instant} field - Hibernate
     * rejects the query at startup.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TenantUsage u
               SET u.used = u.used + :delta,
                   u.updatedAt = :now
             WHERE u.tenantId = :tenantId
               AND u.metric = :metric
            """)
    int incrementExisting(@Param("tenantId") UUID tenantId,
                          @Param("metric") UsageMetric metric,
                          @Param("delta") long delta,
                          @Param("now") java.time.Instant now);

    /**
     * Resets a metered counter for every tenant at once - used by the billing-period rollover job.
     *
     * Sets to zero rather than deleting the rows: the row's existence is what lets
     * {@code incrementExisting} do a single atomic UPDATE on the next use instead of racing to
     * insert. Deleting would reintroduce the insert race this design avoids.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TenantUsage u
               SET u.used = 0,
                   u.updatedAt = :now
             WHERE u.metric = :metric
               AND u.used <> 0
            """)
    int resetMetricForAllTenants(@Param("metric") UsageMetric metric, @Param("now") java.time.Instant now);
}

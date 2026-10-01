package com.zentrox.forge.scheduling;

import com.zentrox.forge.billing.EntitlementService;
import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.entity.SsoLoginState;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.repository.SsoLoginStateRepository;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.TenantUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the job bodies directly rather than waiting for a trigger - the test profile pins the
 * schedules far into the future precisely so nothing fires underneath other tests.
 *
 * Note these call the repository operations, not {@code MaintenanceJobs} itself: the job methods go
 * through {@link SchedulerLock}, which needs a live Redis. The lock is orchestration; the behaviour
 * worth pinning is that the reset and the sweep do the right thing to the data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MaintenanceJobsTest {

    @Autowired
    private TenantUsageRepository usageRepository;
    @Autowired
    private SsoLoginStateRepository loginStateRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private EntitlementService entitlementService;

    @Test
    void usageResetClearsTheMeteredCounterForEveryTenant() {
        Tenant a = newTenant("reset-a");
        Tenant b = newTenant("reset-b");
        entitlementService.recordUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 40);
        entitlementService.recordUsage(b.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 7);

        int reset = usageRepository.resetMetricForAllTenants(
                UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now());

        // The reset is global by design, so the row count it returns depends on whatever else exists
        // in the database - asserting an exact number here would couple this test to the rest of the
        // suite. What matters is the effect on the tenants under test.
        assertThat(reset).isGreaterThanOrEqualTo(2);
        assertThat(entitlementService.currentUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH)).isZero();
        assertThat(entitlementService.currentUsage(b.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH)).isZero();
    }

    @Test
    void usageResetKeepsTheRowSoTheNextIncrementStaysASingleAtomicUpdate() {
        Tenant a = newTenant("reset-keeps-row");
        entitlementService.recordUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 5);

        usageRepository.resetMetricForAllTenants(UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now());

        // Deleting instead of zeroing would reintroduce the insert race recordUsage avoids.
        assertThat(usageRepository.findByTenantIdAndMetric(
                a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH)).isPresent();

        entitlementService.recordUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 3);
        assertThat(entitlementService.currentUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH))
                .isEqualTo(3);
    }

    @Test
    void usageResetIsIdempotentAndTouchesNothingOnASecondRun() {
        Tenant a = newTenant("reset-idempotent");
        entitlementService.recordUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 2);

        usageRepository.resetMetricForAllTenants(UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now());
        assertThat(entitlementService.currentUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH)).isZero();

        // The `used <> 0` predicate means nothing is left to rewrite, so a second sweep is a true
        // no-op - this one CAN assert an exact count, because every row is already zero.
        assertThat(usageRepository.resetMetricForAllTenants(
                UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now())).isZero();
    }

    @Test
    void usageResetLeavesOtherMetricsAlone() {
        Tenant a = newTenant("reset-other-metric");
        entitlementService.recordUsage(a.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 9);
        entitlementService.recordUsage(a.getId(), UsageMetric.API_KEYS, 4);

        usageRepository.resetMetricForAllTenants(UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now());

        assertThat(usageRepository.findByTenantIdAndMetric(a.getId(), UsageMetric.API_KEYS)
                .orElseThrow().getUsed()).isEqualTo(4);
    }

    @Test
    void sweepDeletesExpiredLoginStatesAndKeepsLiveOnes() {
        Tenant tenant = newTenant("sweep-tenant");
        loginStateRepository.save(state(tenant.getId(), "expired-1", Instant.now().minusSeconds(600)));
        loginStateRepository.save(state(tenant.getId(), "expired-2", Instant.now().minusSeconds(60)));
        loginStateRepository.save(state(tenant.getId(), "still-live", Instant.now().plusSeconds(300)));

        int deleted = loginStateRepository.deleteExpired(Instant.now());

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(loginStateRepository.findById("still-live")).isPresent();
        // Only passes because deleteExpired clears the persistence context; a bulk DELETE alone
        // leaves the already-loaded entity in the first-level cache and findById still returns it.
        assertThat(loginStateRepository.findById("expired-1")).isEmpty();
        assertThat(loginStateRepository.findById("expired-2")).isEmpty();
    }

    @Test
    void sweepWithNothingExpiredIsANoOp() {
        Tenant tenant = newTenant("sweep-noop");
        loginStateRepository.save(state(tenant.getId(), "live-only", Instant.now().plusSeconds(600)));

        // Clears anything expired that other tests left behind, so the second call measures only
        // this test's state - which is live, and must not be touched.
        loginStateRepository.deleteExpired(Instant.now());

        assertThat(loginStateRepository.deleteExpired(Instant.now())).isZero();
        assertThat(loginStateRepository.findById("live-only")).isPresent();
    }

    private Tenant newTenant(String slug) {
        return tenantRepository.save(
                Tenant.builder().name(slug).slug(slug).status(TenantStatus.ACTIVE).build());
    }

    private SsoLoginState state(UUID tenantId, String value, Instant expiresAt) {
        return SsoLoginState.builder()
                .state(value)
                .tenantId(tenantId)
                .nonce("nonce-" + value)
                .createdAt(Instant.now().minusSeconds(1))
                .expiresAt(expiresAt)
                .build();
    }
}

package com.zentrox.forge.scheduling;

import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.repository.SsoLoginStateRepository;
import com.zentrox.forge.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodic housekeeping. Both jobs close gaps that the feature modules created and that were
 * previously only documented in the hardening backlog.
 *
 * Every job is idempotent and runs under {@link SchedulerLock}, because {@code @Scheduled} fires on
 * every application instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJobs {

    private final TenantUsageRepository usageRepository;
    private final SsoLoginStateRepository loginStateRepository;
    private final SchedulerLock schedulerLock;

    /**
     * Resets the monthly instance counter at the start of each calendar month.
     *
     * <p>Calendar month, not per-tenant anniversary. The metric is defined as
     * {@code WORKFLOW_INSTANCES_PER_MONTH} and a single global reset is what makes that name true;
     * per-tenant billing anniversaries would need the counter keyed by period, which is a schema
     * change worth making only once real invoicing exists.
     *
     * <p>Without this the counter climbs monotonically and every tenant eventually hits their limit
     * permanently - which is the bug this replaces.
     */
    @Scheduled(cron = "${forge.scheduling.usage-reset-cron:0 5 0 1 * *}", zone = "UTC")
    @Transactional
    public void resetMonthlyUsageCounters() {
        schedulerLock.runIfLockAcquired("usage-reset", Duration.ofMinutes(10), () -> {
            int reset = usageRepository.resetMetricForAllTenants(
                    UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, Instant.now());
            log.info("Monthly usage reset: cleared {} instance counter(s)", reset);
        });
    }

    /**
     * Deletes expired OIDC login states.
     *
     * Every initiated-but-abandoned login leaves a row, so without this the table grows without
     * bound. Hourly is ample: the rows are already unusable the moment they expire (see
     * {@code SsoLoginState#isUsable}), so this is disk hygiene rather than a security control.
     */
    @Scheduled(fixedDelayString = "${forge.scheduling.sso-state-sweep-ms:3600000}",
               initialDelayString = "${forge.scheduling.sso-state-sweep-initial-ms:60000}")
    @Transactional
    public void sweepExpiredSsoLoginStates() {
        schedulerLock.runIfLockAcquired("sso-state-sweep", Duration.ofMinutes(5), () -> {
            int deleted = loginStateRepository.deleteExpired(Instant.now());
            if (deleted > 0) {
                log.info("Deleted {} expired SSO login state(s)", deleted);
            }
        });
    }
}

package com.zentrox.forge.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * A best-effort distributed lock for scheduled jobs, on the Redis connection that already backs
 * refresh tokens and rate limiting.
 *
 * <p><b>Why this is needed at all:</b> {@code @Scheduled} fires on <em>every</em> instance of the
 * application. With two replicas, a monthly usage reset runs twice and an expiry sweep competes with
 * itself. For an idempotent job that is merely wasteful; for anything that reads-then-writes it is a
 * correctness bug.
 *
 * <p><b>Deliberately not a general-purpose lock.</b> It uses {@code SET NX EX}, which is safe for
 * this narrow purpose - the worst case is that a lock expires mid-job and another instance starts a
 * second copy, which both jobs here tolerate because they are idempotent. It is <em>not</em> safe for
 * a job where a duplicate run would be harmful; that needs fencing tokens (Redlock's well-known
 * caveats) or a database-level advisory lock, and this class must not be reused for one.
 *
 * <p><b>Fails closed.</b> If Redis is unreachable the job does not run. That is the opposite choice
 * from the rate limiter, and for the opposite reason: skipping one maintenance sweep costs nothing
 * and it will run again next tick, whereas running an unsynchronised reset across replicas corrupts
 * usage counters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLock {

    private static final String KEY_PREFIX = "forge:sched:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Runs {@code task} only if this instance acquires the lock.
     *
     * @param ttl how long the lock is held. Must exceed the job's expected runtime, or a second
     *            instance could start while the first is still working.
     * @return true if the task ran here
     */
    public boolean runIfLockAcquired(String jobName, Duration ttl, Runnable task) {
        String key = KEY_PREFIX + jobName;
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, "held", ttl);
        } catch (RuntimeException e) {
            log.error("Skipping scheduled job '{}': cannot reach Redis to acquire the lock ({})",
                    jobName, e.getMessage());
            return false;
        }

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Scheduled job '{}' is already running on another instance", jobName);
            return false;
        }

        try {
            task.run();
            return true;
        } finally {
            // Released explicitly so the next scheduled tick is not blocked for the whole TTL. The
            // TTL exists only to recover the lock if this instance dies mid-job.
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not release lock for '{}'; it will expire in {}", jobName, ttl);
            }
        }
    }
}

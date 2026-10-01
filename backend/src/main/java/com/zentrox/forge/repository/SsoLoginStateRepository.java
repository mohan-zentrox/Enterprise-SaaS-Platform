package com.zentrox.forge.repository;

import com.zentrox.forge.entity.SsoLoginState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** Keyed by the opaque state string the IdP echoes back - see SsoLoginState. */
public interface SsoLoginStateRepository extends JpaRepository<SsoLoginState, String> {

    /**
     * Housekeeping for abandoned logins. Without this the table grows by one row per initiated
     * login that was never completed, forever.
     *
     * {@code clearAutomatically} matters even though nothing re-reads these rows in production: a
     * bulk DELETE bypasses the persistence context, so without it an already-loaded entity is still
     * served from the first-level cache after being deleted from the database - which is exactly how
     * this looked broken in testing when it was not.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SsoLoginState s WHERE s.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}

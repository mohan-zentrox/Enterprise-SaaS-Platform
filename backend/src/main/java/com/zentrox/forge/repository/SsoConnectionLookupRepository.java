package com.zentrox.forge.repository;

import com.zentrox.forge.entity.SsoConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Not tenant-scoped, for the same reason as the other lookup repositories: SSO login begins and ends
 * on unauthenticated requests. The login initiation knows only a tenant slug, and the callback knows
 * only an opaque state - in both cases resolving to a tenant is what establishes context, which a
 * tenant-scoped repository by definition cannot do.
 */
public interface SsoConnectionLookupRepository extends JpaRepository<SsoConnection, UUID> {

    Optional<SsoConnection> findByTenantIdAndEnabledTrue(UUID tenantId);
}

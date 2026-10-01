package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.SsoConnection;

import java.util.Optional;
import java.util.UUID;

public interface SsoConnectionRepository extends TenantScopedRepository<SsoConnection, UUID> {

    Optional<SsoConnection> findByTenantId(UUID tenantId);
}

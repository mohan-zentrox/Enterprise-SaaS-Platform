package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.ApiKey;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends TenantScopedRepository<ApiKey, UUID> {

    List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    long countByTenantIdAndRevokedAtIsNull(UUID tenantId);
}

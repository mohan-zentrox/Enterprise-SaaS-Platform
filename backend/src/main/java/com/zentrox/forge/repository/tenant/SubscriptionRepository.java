package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.Subscription;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends TenantScopedRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);
}

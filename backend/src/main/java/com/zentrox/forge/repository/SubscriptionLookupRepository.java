package com.zentrox.forge.repository;

import com.zentrox.forge.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately NOT tenant-scoped, and deliberately in a different package from
 * {@code repository.tenant} so it is excluded from the tenant-scoped repository scan.
 *
 * The billing webhook receiver has no tenant context: it is an unauthenticated request from the
 * payment provider carrying only that provider's subscription id. Resolving that id to a tenant is
 * precisely the lookup a tenant-scoped repository forbids, so it needs its own narrow door - one
 * method, by an identifier only the provider and we know.
 */
public interface SubscriptionLookupRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);
}

package com.zentrox.forge.repository;

import com.zentrox.forge.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately NOT tenant-scoped, for the same reason as SubscriptionLookupRepository: an API-key
 * request arrives with no tenant context at all - resolving the key to its tenant is what
 * establishes it. That lookup is precisely what a tenant-scoped repository forbids, so it gets its
 * own narrow door: one method, keyed on a value only the holder of the key can present.
 */
public interface ApiKeyLookupRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyId(String keyId);
}

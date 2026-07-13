package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends TenantScopedRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}

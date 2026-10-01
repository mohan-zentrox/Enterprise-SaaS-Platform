package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends TenantScopedRepository<Role, UUID> {

    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    List<Role> findAllByTenantIdOrderByNameAsc(UUID tenantId);
}

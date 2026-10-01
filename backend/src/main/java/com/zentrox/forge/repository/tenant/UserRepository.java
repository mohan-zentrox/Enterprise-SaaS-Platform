package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends TenantScopedRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    Page<User> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** Used to block deletion of a role that still has members, and to protect the last owner. */
    long countByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    long countByTenantIdAndRoleIdAndStatus(UUID tenantId, UUID roleId, com.zentrox.forge.entity.UserStatus status);

    /** Live seat count for entitlement checks - deactivated users do not consume a seat. */
    long countByTenantIdAndStatus(UUID tenantId, com.zentrox.forge.entity.UserStatus status);
}

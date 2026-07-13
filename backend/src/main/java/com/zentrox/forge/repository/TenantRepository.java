package com.zentrox.forge.repository;

import com.zentrox.forge.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately NOT tenant-scoped (see repository.tenant package for that pattern):
 * a Tenant row IS the isolation boundary, so looking one up by id/slug is a global,
 * cross-tenant operation by definition (e.g. resolving which tenant a login belongs to).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Base class for every entity that belongs to exactly one tenant.
 *
 * Every subclass MUST be read/written exclusively through a repository extending
 * {@link com.zentrox.forge.repository.tenant.TenantScopedRepository}, never a plain
 * {@code JpaRepository}. That base repository disables the inherited, tenant-unaware
 * {@code findById}/{@code findAll} methods at runtime (see TenantScopedRepositoryImpl)
 * so that a developer who "forgets" to filter by tenant gets a hard failure instead of
 * a silent cross-tenant data leak. This is in addition to (not instead of) the
 * Hibernate {@code @Filter} enabled per-request by TenantFilterInterceptor.
 *
 * Uses Lombok {@code @SuperBuilder} (not plain {@code @Builder}): every subclass must
 * also use {@code @SuperBuilder} so {@code id}/{@code tenantId} are reachable from the
 * subclass's generated builder - plain {@code @Builder} on a subclass silently ignores
 * inherited fields.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}

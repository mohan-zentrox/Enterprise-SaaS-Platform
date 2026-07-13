package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.TenantScopedEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository for every entity that extends {@link TenantScopedEntity}. All
 * tenant-scoped repositories (UserRepository, RoleRepository, WorkflowDefinitionRepository,
 * WorkflowInstanceRepository, AuditLogRepository) extend this instead of {@code JpaRepository}
 * directly.
 *
 * The tenant-aware finder methods below are the ONLY sanctioned way to read/write a
 * single row by id. The inherited {@code findById}/{@code findAll}/{@code deleteById}
 * from {@code JpaRepository} are intentionally overridden to throw at runtime - see
 * {@link TenantScopedRepositoryImpl} - so a call site that "forgets" the tenant filter
 * fails loudly in tests/dev instead of silently returning another tenant's row.
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantScopedEntity, ID> extends JpaRepository<T, ID> {

    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    List<T> findAllByTenantId(UUID tenantId);

    Page<T> findAllByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(ID id, UUID tenantId);
}

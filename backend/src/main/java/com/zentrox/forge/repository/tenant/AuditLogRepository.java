package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Append-only: this interface intentionally exposes no update/delete beyond what
 * {@link TenantScopedRepository} provides, and nothing in the codebase calls those.
 */
public interface AuditLogRepository extends TenantScopedRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByTenantIdOrderByTimestampDesc(UUID tenantId, Pageable pageable);
}

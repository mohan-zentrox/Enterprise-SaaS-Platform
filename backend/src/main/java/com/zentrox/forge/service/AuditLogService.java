package com.zentrox.forge.service;

import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.dto.audit.AuditLogResponse;
import com.zentrox.forge.repository.tenant.AuditLogRepository;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * FRD Section 8 (Audit) - read side, behind AUDIT_LOG_READ.
 *
 * Audit rows have been written since the first commit but were unreadable through the API, which
 * made the whole trail useless to the compliance reviewers it exists for. Read-only by design:
 * there is deliberately no write, update or delete path here - {@link com.zentrox.forge.aop.AuditAspect}
 * is the only writer.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public PageResponse<AuditLogResponse> listEntries(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return PageResponse.from(
                auditLogRepository.findAllByTenantIdOrderByTimestampDesc(tenantId, pageable),
                AuditLogResponse::from);
    }
}

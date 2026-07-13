package com.zentrox.forge.aop;

import com.zentrox.forge.dto.TenantCreateResponse;
import com.zentrox.forge.dto.workflow.WorkflowDefinitionResponse;
import com.zentrox.forge.dto.workflow.WorkflowInstanceResponse;
import com.zentrox.forge.entity.AuditLog;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantScopedEntity;
import com.zentrox.forge.repository.tenant.AuditLogRepository;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FRD Section 8 (Audit): writes an append-only {@link AuditLog} row after any service
 * method annotated {@link Audited} completes successfully. Deliberately does NOT run
 * on failure (an @AfterThrowing advice would need its own row shape / status field to
 * be meaningful - left as a follow-up, see docs/ARCHITECTURE.md "Hardening backlog").
 *
 * Failure to write the audit row never fails the underlying business operation - it is
 * logged and swallowed, since audit logging is a secondary concern to the write itself.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void audit(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                log.debug("Skipping audit log for {} - no tenant bound to context", audited.action());
                return;
            }

            AuditLog entry = AuditLog.builder()
                    .tenantId(tenantId)
                    .actorUserId(SecurityUtils.currentUserId().orElse(null))
                    .action(audited.action())
                    .entityType(audited.entityType())
                    .entityId(resolveEntityId(result, joinPoint.getArgs()))
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}: {}", audited.action(), e.getMessage(), e);
        }
    }

    private String resolveEntityId(Object result, Object[] args) {
        if (result instanceof TenantScopedEntity entity) {
            return entity.getId() != null ? entity.getId().toString() : null;
        }
        if (result instanceof Tenant tenant) {
            return tenant.getId().toString();
        }
        if (result instanceof TenantCreateResponse response) {
            return response.tenant().id().toString();
        }
        if (result instanceof WorkflowDefinitionResponse response) {
            return response.id().toString();
        }
        if (result instanceof WorkflowInstanceResponse response) {
            return response.id().toString();
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid.toString();
            }
        }
        return null;
    }
}

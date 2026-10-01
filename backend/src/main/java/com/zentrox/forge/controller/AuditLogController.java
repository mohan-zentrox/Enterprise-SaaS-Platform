package com.zentrox.forge.controller;

import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.dto.audit.AuditLogResponse;
import com.zentrox.forge.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FRD Section 8 (Audit) - read side. Newest first, paginated, tenant-scoped, read-only: there is
 * no write/update/delete endpoint here by design (see AuditLogService).
 */
@RestController
@RequestMapping("/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    public ResponseEntity<PageResponse<AuditLogResponse>> listEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditLogService.listEntries(PageRequests.of(page, size, MAX_PAGE_SIZE)));
    }
}

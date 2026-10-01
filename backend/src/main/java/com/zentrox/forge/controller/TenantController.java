package com.zentrox.forge.controller;

import com.zentrox.forge.dto.TenantCreateRequest;
import com.zentrox.forge.dto.TenantCreateResponse;
import com.zentrox.forge.dto.TenantResponse;
import com.zentrox.forge.dto.TenantUpdateRequest;
import com.zentrox.forge.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FRD Section 3: tenant self-signup. Public - no authentication exists yet at this point. */
@RestController
@RequestMapping("/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantCreateResponse> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        TenantCreateResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ------------------------------------------------- administration of the caller's own tenant
    // Addressed as /current rather than /{id}: there is intentionally no way to name another
    // tenant in a URL, so cross-tenant administration is impossible by construction rather than
    // by a permission check that a future contributor might forget.

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public ResponseEntity<TenantResponse> getCurrentTenant() {
        return ResponseEntity.ok(tenantService.getCurrentTenant());
    }

    @PatchMapping("/current")
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<TenantResponse> updateCurrentTenant(@Valid @RequestBody TenantUpdateRequest request) {
        return ResponseEntity.ok(tenantService.renameCurrentTenant(request));
    }

    @PostMapping("/current/suspend")
    @PreAuthorize("hasAuthority('TENANT_SUSPEND')")
    public ResponseEntity<TenantResponse> suspendCurrentTenant() {
        return ResponseEntity.ok(tenantService.suspendCurrentTenant());
    }

    @PostMapping("/current/activate")
    @PreAuthorize("hasAuthority('TENANT_SUSPEND')")
    public ResponseEntity<TenantResponse> activateCurrentTenant() {
        return ResponseEntity.ok(tenantService.activateCurrentTenant());
    }
}

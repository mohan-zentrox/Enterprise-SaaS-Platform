package com.zentrox.forge.controller;

import com.zentrox.forge.dto.TenantCreateRequest;
import com.zentrox.forge.dto.TenantCreateResponse;
import com.zentrox.forge.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}

package com.zentrox.forge.publicapi;

import com.zentrox.forge.publicapi.dto.ApiKeyCreateRequest;
import com.zentrox.forge.publicapi.dto.ApiKeyCreatedResponse;
import com.zentrox.forge.publicapi.dto.ApiKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Management of a tenant's API keys - on the authenticated surface, not the public one.
 *
 * Guarded by ROLE_MANAGE rather than a key-specific permission: issuing an API key grants standing
 * programmatic access to tenant data, which is the same class of decision as defining a role. It is
 * emphatically not something USER_READ should allow.
 */
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /** The response contains the only copy of the secret that will ever exist. */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiKeyCreatedResponse> createKey(@Valid @RequestBody ApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.createKey(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<List<ApiKeyResponse>> listKeys() {
        return ResponseEntity.ok(apiKeyService.listKeys());
    }

    /** Revokes rather than deletes - see ApiKeyService#revokeKey. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiKeyResponse> revokeKey(@PathVariable UUID id) {
        return ResponseEntity.ok(apiKeyService.revokeKey(id));
    }
}

package com.zentrox.forge.sso;

import com.zentrox.forge.sso.dto.SsoConnectionRequest;
import com.zentrox.forge.sso.dto.SsoConnectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FRD-13.1 - administering the caller's own tenant's SSO connection.
 *
 * <p>Deliberately on a different path from the login endpoints: {@code /v1/tenants/current/sso} is
 * authenticated tenant administration, while {@code /v1/sso/**} is the unauthenticated login flow. If
 * they shared a prefix, the {@code permitAll} rule that the login flow needs would also expose this
 * configuration surface - which holds IdP client credentials.
 *
 * <p>Guarded by TENANT_UPDATE: whoever controls the IdP connection controls who can sign in, so this
 * is among the most privileged operations in the platform.
 */
@RestController
@RequestMapping("/v1/tenants/current/sso")
@RequiredArgsConstructor
public class SsoConnectionController {

    private final SsoConnectionService ssoConnectionService;

    /** Never returns the client secret - only whether one is set. See SsoConnectionResponse. */
    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<SsoConnectionResponse> getConnection() {
        return ResponseEntity.ok(ssoConnectionService.getConnection());
    }

    /** Create or replace. Omitting clientSecret leaves the stored one unchanged. */
    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<SsoConnectionResponse> upsertConnection(
            @Valid @RequestBody SsoConnectionRequest request) {
        return ResponseEntity.ok(ssoConnectionService.upsertConnection(request));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<Void> deleteConnection() {
        ssoConnectionService.deleteConnection();
        return ResponseEntity.noContent().build();
    }
}

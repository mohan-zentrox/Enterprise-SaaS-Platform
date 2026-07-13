package com.zentrox.forge.sso;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCAFFOLD ONLY - FRD Section 13 (SAML/OIDC SSO).
 *
 * TODO(FRD-13.1): Add `sso_connections` (tenant_id, protocol [SAML|OIDC], metadata/config,
 *   enabled) so each tenant can bring their own IdP.
 * TODO(FRD-13.2): For OIDC, integrate spring-security-oauth2-client; for SAML, integrate
 *   spring-security-saml2-service-provider. Both should terminate in the SAME place as
 *   password login: AuthService issuing our own access+refresh token pair (see
 *   AuthService#login) so the rest of the platform doesn't need to know how the user
 *   authenticated.
 * TODO(FRD-13.3): Map IdP group/role claims to Forge Roles (see RoleCatalog) via a
 *   configurable per-tenant mapping - do not hardcode a 1:1 assumption.
 * TODO(FRD-13.4): This must remain tenant-scoped: the IdP metadata and callback URL are
 *   per-tenant (likely /v1/sso/{tenantSlug}/callback), never global.
 */
@RestController
@RequestMapping("/v1/sso")
public class SsoController {

    @PostMapping("/{tenantSlug}/callback")
    public ResponseEntity<Void> callback() {
        // TODO(FRD-13.2): not yet implemented.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}

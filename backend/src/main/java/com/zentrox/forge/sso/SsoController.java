package com.zentrox.forge.sso;

import com.zentrox.forge.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * FRD-13.2 / FRD-13.4 - the per-tenant SSO login endpoints.
 *
 * Both are unauthenticated by necessity: they are how a user becomes authenticated. Both are scoped
 * by tenant slug in the path, so there is no global SSO entry point - which is FRD-13.4's
 * requirement, and also means one tenant's IdP can never be used to log in to another.
 */
@RestController
@RequestMapping("/v1/sso")
@RequiredArgsConstructor
public class SsoController {

    private final OidcLoginService oidcLoginService;

    /**
     * Starts the flow: 302 to the tenant's identity provider.
     *
     * A redirect rather than returning the URL as JSON, so this can be used directly as an
     * {@code <a href>} from a login page with no JavaScript involved.
     */
    @GetMapping("/{tenantSlug}/login")
    public ResponseEntity<Void> beginLogin(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) String redirectUri) {
        String authorizationUrl = oidcLoginService.beginLogin(tenantSlug, redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizationUrl)).build();
    }

    /**
     * The IdP redirects the browser here with the authorization code.
     *
     * <p>Returns the token pair as JSON rather than redirecting with tokens in the URL. Putting a
     * refresh token in a query string would write a long-lived credential into browser history,
     * server access logs and any {@code Referer} header - which is why the OAuth specs warn against
     * exactly this pattern.
     *
     * <p>Exposed as both GET (the standard IdP redirect) and POST (for providers configured to use
     * {@code response_mode=form_post}).
     */
    @GetMapping("/{tenantSlug}/callback")
    public ResponseEntity<AuthResponse> callbackGet(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        return ResponseEntity.ok(oidcLoginService.completeLogin(tenantSlug, code, state));
    }

    @PostMapping("/{tenantSlug}/callback")
    public ResponseEntity<AuthResponse> callbackPost(
            @PathVariable String tenantSlug,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        return ResponseEntity.ok(oidcLoginService.completeLogin(tenantSlug, code, state));
    }
}

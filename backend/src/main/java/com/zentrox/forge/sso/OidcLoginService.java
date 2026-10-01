package com.zentrox.forge.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrox.forge.dto.AuthResponse;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.SsoConnection;
import com.zentrox.forge.entity.SsoLoginState;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.InvalidCredentialsException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.SsoConnectionLookupRepository;
import com.zentrox.forge.repository.SsoLoginStateRepository;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * FRD-13.2 - the OIDC authorization-code flow, per tenant.
 *
 * <p><b>It terminates in {@link AuthService#issueTokensForSsoLogin}</b>, the same token issuance as
 * password login. That is the single most important design decision here: nothing downstream of
 * authentication needs to know how a user proved who they are. The alternative - a parallel session
 * mechanism for SSO users - would mean every authorization check, every refresh path and every audit
 * entry had two cases to handle forever.
 *
 * <p>Security properties, each present for a specific attack:
 * <ul>
 *   <li><b>state</b> is generated server-side, stored, and verified on callback. Without it an
 *       attacker can hand a victim a callback URL bearing the attacker's authorization code and log
 *       the victim into the attacker's account (login CSRF).</li>
 *   <li><b>state is single-use.</b> Consumed on redemption, so a captured callback URL cannot be
 *       replayed.</li>
 *   <li><b>nonce</b> is bound to the state and checked against the id_token claim, which ties the
 *       token to this specific authorization request and defeats token replay from another session.</li>
 *   <li><b>The tenant comes from the stored state</b>, never from the callback's query string, so a
 *       code obtained for one tenant cannot be redeemed against another.</li>
 * </ul>
 *
 * <p><b>Known limitation, stated rather than hidden:</b> the id_token signature is not verified
 * against the IdP's JWKS. It does not have to be for this flow to be secure - the token is fetched by
 * us directly from the IdP's token endpoint over TLS using our client credentials, so it is not
 * attacker-supplied. Signature verification becomes mandatory the moment an implicit or hybrid flow
 * is added, where the token does arrive via the browser. {@code jwksUri} is captured and stored ready
 * for that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoConnectionLookupRepository connectionLookupRepository;
    private final SsoLoginStateRepository loginStateRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final IdpRoleMapper roleMapper;
    private final SecretCipher secretCipher;
    private final SsoProperties ssoProperties;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    /**
     * Builds the IdP authorization URL the browser should be redirected to, and records the state
     * that the callback will be checked against.
     */
    @Transactional
    public String beginLogin(String tenantSlug, String postLoginRedirect) {
        Tenant tenant = requireActiveTenant(tenantSlug);
        SsoConnection connection = requireEnabledConnection(tenant.getId());

        String state = randomToken();
        String nonce = randomToken();

        loginStateRepository.save(SsoLoginState.builder()
                .state(state)
                .tenantId(tenant.getId())
                .nonce(nonce)
                .redirectUri(postLoginRedirect)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(ssoProperties.loginStateTtlSeconds()))
                .build());

        return UriComponentsBuilder.fromUriString(connection.getAuthorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", connection.getClientId())
                .queryParam("redirect_uri", callbackUri(tenantSlug))
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                // encode() is required, not optional: UriComponentsBuilder does NOT escape query
                // values by default, so the spaces in the scope parameter (and any reserved
                // character in a redirect URI) produce a string that URI.create rejects outright.
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Redeems the authorization code and issues Forge tokens.
     *
     * @return the same access+refresh pair a password login produces
     */
    @Transactional
    public AuthResponse completeLogin(String tenantSlug, String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new InvalidCredentialsException("Incomplete SSO callback");
        }

        SsoLoginState loginState = loginStateRepository.findById(state)
                .filter(s -> s.isUsable(Instant.now()))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "This sign-in link has expired or already been used. Start again."));

        // Single-use: consumed before the exchange, so even a concurrent replay cannot redeem twice.
        loginState.setConsumedAt(Instant.now());
        loginStateRepository.save(loginState);

        Tenant tenant = requireActiveTenant(tenantSlug);
        // The state carries the tenant it was issued for; a code minted for one tenant must not be
        // redeemable at another tenant's callback.
        if (!tenant.getId().equals(loginState.getTenantId())) {
            throw new InvalidCredentialsException("This sign-in request was not issued for this organization");
        }

        SsoConnection connection = requireEnabledConnection(tenant.getId());
        JsonNode claims = exchangeCodeForClaims(connection, code, tenantSlug);

        if (!loginState.getNonce().equals(text(claims, "nonce"))) {
            throw new InvalidCredentialsException("SSO nonce mismatch; the sign-in could not be verified");
        }

        String email = text(claims, connection.getEmailClaim());
        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException(
                    "The identity provider did not return a '" + connection.getEmailClaim() + "' claim");
        }

        User user = resolveUser(tenant, connection, claims, email);
        return authService.issueTokensForSsoLogin(user);
    }

    // ------------------------------------------------------------------ internals

    private JsonNode exchangeCodeForClaims(SsoConnection connection, String code, String tenantSlug) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", callbackUri(tenantSlug));
        form.add("client_id", connection.getClientId());
        form.add("client_secret", secretCipher.decrypt(connection.getClientSecret()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        JsonNode tokenResponse;
        try {
            String body = restClient.post()
                    .uri(connection.getTokenEndpoint())
                    .headers(h -> h.addAll(headers))
                    .body(new HttpEntity<>(form, headers).getBody())
                    .retrieve()
                    .body(String.class);
            tokenResponse = objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            // Never surface the provider's raw error: it can echo the client secret back in some
            // implementations, and it means nothing to the person who just tried to sign in.
            log.error("OIDC token exchange failed for tenant {}: {}", tenantSlug, e.getMessage());
            throw new InvalidCredentialsException("Could not complete sign-in with your identity provider");
        }

        String idToken = text(tokenResponse, "id_token");
        if (idToken == null) {
            throw new InvalidCredentialsException("The identity provider did not return an id_token");
        }
        return decodeIdTokenClaims(idToken);
    }

    /**
     * Reads the id_token payload without verifying its signature - see the class javadoc for why
     * that is sound for a confidential-client authorization-code flow, and when it would stop being.
     */
    private JsonNode decodeIdTokenClaims(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new InvalidCredentialsException("Malformed id_token");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new InvalidCredentialsException("Could not read the id_token returned by your identity provider");
        }
    }

    /**
     * Finds the Forge user for an authenticated identity, provisioning one if the connection permits.
     *
     * With {@code autoProvision} false, SSO authenticates only users an administrator has already
     * invited - which is what a customer who wants SSO as an access-control boundary expects. With it
     * true, anyone their IdP will vouch for gets an account, so the role comes from the group mapping.
     */
    private User resolveUser(Tenant tenant, SsoConnection connection, JsonNode claims, String email) {
        String resolvedRole = roleMapper.resolveRole(
                connection.getRoleMappingJson(), groupsFrom(claims, connection.getGroupsClaim()),
                connection.getDefaultRole());

        return userRepository.findByTenantIdAndEmail(tenant.getId(), email)
                .map(existing -> {
                    if (existing.getStatus() != UserStatus.ACTIVE) {
                        // A deactivated account must not be revived by an IdP assertion.
                        throw new InvalidCredentialsException("This account is disabled");
                    }
                    // Keep role in sync with the directory on every login: that is the point of
                    // central identity management, and it means removing someone from an admin group
                    // actually demotes them here.
                    roleRepository.findByTenantIdAndName(tenant.getId(), resolvedRole)
                            .ifPresent(existing::setRole);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    if (!connection.isAutoProvision()) {
                        throw new InvalidCredentialsException(
                                "No account exists for " + email + " in this organization. "
                                        + "Ask an administrator to invite you.");
                    }
                    Role role = roleRepository.findByTenantIdAndName(tenant.getId(), resolvedRole)
                            .orElseThrow(() -> new IllegalStateException(
                                    "SSO resolved role '" + resolvedRole + "' which does not exist"));
                    return userRepository.save(User.builder()
                            .tenantId(tenant.getId())
                            .email(email)
                            .fullName(firstNonBlank(text(claims, "name"), email))
                            // No password: this account authenticates only through the IdP. The
                            // placeholder is deliberately not a valid bcrypt hash, so it can never
                            // match any presented password at the password-login endpoint.
                            .passwordHash("{sso}no-password-login")
                            .role(role)
                            .status(UserStatus.ACTIVE)
                            .build());
                });
    }

    private List<String> groupsFrom(JsonNode claims, String groupsClaim) {
        if (groupsClaim == null || groupsClaim.isBlank()) {
            return List.of();
        }
        JsonNode node = claims.get(groupsClaim);
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> groups = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(element -> groups.add(element.asText()));
        } else {
            // Some providers send a single group as a scalar, or space/comma separated.
            for (String part : node.asText().split("[,\\s]+")) {
                if (!part.isBlank()) {
                    groups.add(part.trim());
                }
            }
        }
        return groups;
    }

    private Tenant requireActiveTenant(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Unknown organization: " + slug));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException("This organization is suspended");
        }
        return tenant;
    }

    private SsoConnection requireEnabledConnection(UUID tenantId) {
        return connectionLookupRepository.findByTenantIdAndEnabledTrue(tenantId)
                .orElseThrow(() -> new NotFoundException("Single sign-on is not enabled for this organization"));
    }

    private String callbackUri(String tenantSlug) {
        // Built from configuration, not from the request - see SsoProperties#baseUrl.
        return ssoProperties.baseUrl() + "/v1/sso/" + tenantSlug + "/callback";
    }

    private String randomToken() {
        byte[] buffer = new byte[32];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }
}

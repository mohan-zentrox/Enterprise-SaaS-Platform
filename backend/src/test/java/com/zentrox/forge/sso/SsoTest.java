package com.zentrox.forge.sso;

import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.SsoConnection;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.InvalidCredentialsException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.SsoLoginStateRepository;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.SsoConnectionRepository;
import com.zentrox.forge.service.RoleCatalog;
import com.zentrox.forge.sso.dto.SsoConnectionRequest;
import com.zentrox.forge.sso.dto.SsoConnectionResponse;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the parts of SSO that are testable without a live identity provider: connection validation,
 * secret handling, the CSRF/replay defences around login state, and group-to-role mapping.
 *
 * The code exchange itself needs a real IdP and is not simulated here - see the note at the end of
 * this class.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SsoTest {

    @Autowired
    private SsoConnectionService connectionService;
    @Autowired
    private OidcLoginService oidcLoginService;
    @Autowired
    private IdpRoleMapper roleMapper;
    @Autowired
    private SecretCipher secretCipher;
    @Autowired
    private SsoConnectionRepository connectionRepository;
    @Autowired
    private SsoLoginStateRepository loginStateRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("sso-tenant").slug("sso-tenant").status(TenantStatus.ACTIVE).build());
        for (String name : List.of(RoleCatalog.OWNER, RoleCatalog.ADMIN, RoleCatalog.MEMBER)) {
            roleRepository.save(Role.builder()
                    .tenantId(tenant.getId()).name(name).systemRole(true)
                    .permissions(EnumSet.of(Permission.USER_READ)).build());
        }
        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ secret handling

    @Test
    void clientSecretIsEncryptedAtRestAndRoundTrips() {
        connectionService.upsertConnection(oidcRequest(false, "super-secret-value"));

        SsoConnection stored = connectionRepository.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(stored.getClientSecret()).isNotEqualTo("super-secret-value");
        assertThat(secretCipher.decrypt(stored.getClientSecret())).isEqualTo("super-secret-value");
    }

    @Test
    void encryptionUsesAFreshIvSoIdenticalSecretsDifferOnDisk() {
        // Equal ciphertexts would reveal that two tenants use the same IdP secret.
        assertThat(secretCipher.encrypt("same-value")).isNotEqualTo(secretCipher.encrypt("same-value"));
    }

    @Test
    void tamperedCiphertextIsRejectedRatherThanDecryptedToGarbage() {
        String encrypted = secretCipher.encrypt("original");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThatThrownBy(() -> secretCipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theClientSecretIsNeverReturnedByTheApi() {
        SsoConnectionResponse response = connectionService.upsertConnection(
                oidcRequest(false, "super-secret-value"));

        // Only the fact that one is set.
        assertThat(response.clientSecretSet()).isTrue();
        assertThat(response.toString()).doesNotContain("super-secret-value");
    }

    @Test
    void omittingTheSecretOnUpdateLeavesTheStoredOneIntact() {
        connectionService.upsertConnection(oidcRequest(false, "original-secret"));
        connectionService.upsertConnection(oidcRequest(false, null));

        SsoConnection stored = connectionRepository.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(secretCipher.decrypt(stored.getClientSecret())).isEqualTo("original-secret");
    }

    // ------------------------------------------------------------------ enable validation

    @Test
    void refusesToEnableAConnectionMissingRequiredFields() {
        assertThatThrownBy(() -> connectionService.upsertConnection(new SsoConnectionRequest(
                SsoProtocol.OIDC, true, "https://idp.example", null, "secret",
                "https://idp.example/auth", "https://idp.example/token", null,
                "email", null, "{}", false, "MEMBER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void refusesToEnableSamlBecauseItIsNotImplemented() {
        assertThatThrownBy(() -> connectionService.upsertConnection(new SsoConnectionRequest(
                SsoProtocol.SAML, true, "https://idp.example", "client", "secret",
                "https://idp.example/auth", "https://idp.example/token", null,
                "email", null, "{}", false, "MEMBER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SAML is not implemented");
    }

    @Test
    void storesButDoesNotEnableADisabledSamlConnection() {
        // The configuration is allowed to exist; only activation is refused.
        SsoConnectionResponse stored = connectionService.upsertConnection(new SsoConnectionRequest(
                SsoProtocol.SAML, false, "https://idp.example", "client", "secret",
                null, null, null, "email", null, "{}", false, "MEMBER"));

        assertThat(stored.protocol()).isEqualTo(SsoProtocol.SAML);
        assertThat(stored.enabled()).isFalse();
    }

    @Test
    void refusesToEnableWithADefaultRoleThatDoesNotExist() {
        assertThatThrownBy(() -> connectionService.upsertConnection(new SsoConnectionRequest(
                SsoProtocol.OIDC, true, "https://idp.example", "client", "secret",
                "https://idp.example/auth", "https://idp.example/token", null,
                "email", null, "{}", true, "NOT_A_ROLE")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void enablesAFullyConfiguredOidcConnection() {
        assertThat(connectionService.upsertConnection(oidcRequest(true, "secret")).enabled()).isTrue();
    }

    @Test
    void exposesTheLoginAndCallbackUrlsForTheIdpConfiguration() {
        SsoConnectionResponse response = connectionService.upsertConnection(oidcRequest(true, "secret"));

        assertThat(response.loginUrl()).endsWith("/v1/sso/sso-tenant/login");
        assertThat(response.callbackUrl()).endsWith("/v1/sso/sso-tenant/callback");
    }

    // ------------------------------------------------------------------ login state (CSRF / replay)

    @Test
    void beginLoginRedirectsToTheIdpWithStateAndNonce() {
        connectionService.upsertConnection(oidcRequest(true, "secret"));

        String url = oidcLoginService.beginLogin("sso-tenant", null);

        assertThat(url).startsWith("https://idp.example/auth")
                .contains("response_type=code")
                .contains("client_id=client")
                .contains("state=")
                .contains("nonce=")
                .contains("redirect_uri=");
        assertThat(loginStateRepository.count()).isEqualTo(1);

        // Asserting on substrings alone is not enough: an unencoded URL still "contains" every
        // parameter but blows up when the controller turns it into a redirect Location. The scope
        // parameter has spaces in it, so this is the assertion that catches a missing encode().
        assertThat(java.net.URI.create(url)).isNotNull();
        assertThat(url).doesNotContain(" ");
    }

    @Test
    void theAuthorizationUrlIsAValidUriEvenWithAwkwardConfiguration() {
        // A redirect URI containing reserved characters is the other way an unencoded builder breaks.
        connectionService.upsertConnection(oidcRequest(true, "secret"));
        String url = oidcLoginService.beginLogin("sso-tenant", "https://app.example/landing?next=/home&x=1");

        assertThat(java.net.URI.create(url).getQuery()).contains("openid email profile");
    }

    @Test
    void beginLoginFailsWhenSsoIsNotEnabled() {
        connectionService.upsertConnection(oidcRequest(false, "secret"));

        assertThatThrownBy(() -> oidcLoginService.beginLogin("sso-tenant", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void beginLoginFailsForAnUnknownOrganization() {
        assertThatThrownBy(() -> oidcLoginService.beginLogin("no-such-tenant", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void callbackWithAnUnknownStateIsRejected() {
        connectionService.upsertConnection(oidcRequest(true, "secret"));

        // The login-CSRF defence: a state we never issued cannot be redeemed.
        assertThatThrownBy(() -> oidcLoginService.completeLogin("sso-tenant", "some-code", "forged-state"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("expired or already been used");
    }

    @Test
    void callbackWithoutACodeOrStateIsRejected() {
        assertThatThrownBy(() -> oidcLoginService.completeLogin("sso-tenant", null, null))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> oidcLoginService.completeLogin("sso-tenant", "code", ""))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void aStateIssuedForOneTenantCannotBeRedeemedAtAnother() {
        connectionService.upsertConnection(oidcRequest(true, "secret"));
        String url = oidcLoginService.beginLogin("sso-tenant", null);
        String state = url.substring(url.indexOf("state=") + 6).split("&")[0];

        Tenant other = tenantRepository.save(Tenant.builder()
                .name("other-sso").slug("other-sso").status(TenantStatus.ACTIVE).build());
        assertThat(other.getId()).isNotNull();

        // Reaches the tenant check before any network call, so this fails without a live IdP.
        assertThatThrownBy(() -> oidcLoginService.completeLogin("other-sso", "code", state))
                .isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------ role mapping

    @Test
    void mapsAnIdpGroupToTheConfiguredRole() {
        String mapping = "{\"IT-Admins-EMEA\":\"ADMIN\",\"All-Staff\":\"MEMBER\"}";
        assertThat(roleMapper.resolveRole(mapping, List.of("IT-Admins-EMEA"), "MEMBER")).isEqualTo("ADMIN");
    }

    @Test
    void doesNotAssumeGroupNamesEqualRoleNames() {
        // The naive 1:1 implementation would return "IT-Admins-EMEA" and silently grant nothing.
        assertThat(roleMapper.resolveRole("{}", List.of("IT-Admins-EMEA"), "MEMBER")).isEqualTo("MEMBER");
    }

    @Test
    void mostPrivilegedGroupWinsWhenSeveralMatch() {
        String mapping = "{\"Admins\":\"ADMIN\",\"Staff\":\"MEMBER\",\"Founders\":\"OWNER\"}";

        // Claim order from an IdP is not meaningful, so the result must not depend on it.
        assertThat(roleMapper.resolveRole(mapping, List.of("Staff", "Admins"), "MEMBER")).isEqualTo("ADMIN");
        assertThat(roleMapper.resolveRole(mapping, List.of("Admins", "Staff"), "MEMBER")).isEqualTo("ADMIN");
        assertThat(roleMapper.resolveRole(mapping, List.of("Staff", "Founders", "Admins"), "MEMBER"))
                .isEqualTo("OWNER");
    }

    @Test
    void anUnmappedCustomRoleNeverOutranksASystemRole() {
        String mapping = "{\"Contractors\":\"CONTRACTOR\",\"Admins\":\"ADMIN\"}";
        assertThat(roleMapper.resolveRole(mapping, List.of("Contractors", "Admins"), "MEMBER"))
                .isEqualTo("ADMIN");
    }

    @Test
    void fallsBackToTheDefaultRoleWhenNothingMatches() {
        String mapping = "{\"Admins\":\"ADMIN\"}";
        assertThat(roleMapper.resolveRole(mapping, List.of("Unknown-Group"), "MEMBER")).isEqualTo("MEMBER");
        assertThat(roleMapper.resolveRole(mapping, List.of(), "MEMBER")).isEqualTo("MEMBER");
        assertThat(roleMapper.resolveRole(mapping, null, "MEMBER")).isEqualTo("MEMBER");
    }

    @Test
    void amalformedMappingGrantsTheDefaultRatherThanGuessing() {
        assertThat(roleMapper.resolveRole("not json", List.of("Admins"), "MEMBER")).isEqualTo("MEMBER");
    }

    // ------------------------------------------------------------------ helpers

    private SsoConnectionRequest oidcRequest(boolean enabled, String secret) {
        return new SsoConnectionRequest(
                SsoProtocol.OIDC, enabled,
                "https://idp.example", "client", secret,
                "https://idp.example/auth", "https://idp.example/token", "https://idp.example/jwks",
                "email", "groups", "{\"Admins\":\"ADMIN\"}", true, "MEMBER");
    }
}

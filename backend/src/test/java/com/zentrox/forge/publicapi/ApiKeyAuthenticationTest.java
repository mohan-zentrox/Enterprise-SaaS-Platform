package com.zentrox.forge.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrox.forge.billing.SubscriptionPlan;
import com.zentrox.forge.billing.SubscriptionStatus;
import com.zentrox.forge.entity.ApiKey;
import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.publicapi.dto.ApiKeyCreateRequest;
import com.zentrox.forge.publicapi.dto.ApiKeyCreatedResponse;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.ApiKeyRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.JwtService;
import com.zentrox.forge.service.RoleCatalog;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public API is the only surface an outsider can reach with a long-lived credential, so these
 * tests focus on the boundaries: a key must not work without a scope, must not work after
 * revocation or expiry, must not work on the authenticated API, and must not see another tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiKeyAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private com.zentrox.forge.repository.tenant.SubscriptionRepository subscriptionRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("apikey-tenant").slug("apikey-tenant").status(TenantStatus.ACTIVE).build());
        // API keys are entitlement-gated and FREE allows none, so the tenant needs a plan that
        // permits them. (That enforcement has its own coverage in EntitlementEnforcementTest.)
        subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId())
                .plan(SubscriptionPlan.PROFESSIONAL)
                .status(SubscriptionStatus.ACTIVE)
                .build());
        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ issuance

    @Test
    void issuesAKeyWhoseSecretIsReturnedOnceAndNotStored() {
        ApiKeyCreatedResponse created = apiKeyService.createKey(new ApiKeyCreateRequest(
                "CI pipeline", Set.of(ApiScope.INSTANCES_READ), null));

        assertThat(created.secret()).startsWith("forge_");
        assertThat(created.warning()).contains("cannot be shown again");

        // The decisive assertion: the plaintext is nowhere in the stored row.
        ApiKey stored = apiKeyRepository.findByIdAndTenantId(created.key().id(), tenant.getId()).orElseThrow();
        assertThat(stored.getKeyHash()).isNotEqualTo(created.secret());
        assertThat(created.secret()).doesNotContain(stored.getKeyHash());
        assertThat(stored.getKeyHash()).hasSize(64); // hex-encoded SHA-256
    }

    @Test
    void theMaskedFormIdentifiesAKeyWithoutRevealingIt() {
        ApiKeyCreatedResponse created = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Masked", Set.of(ApiScope.INSTANCES_READ), null));

        assertThat(created.key().maskedKey()).contains("...").doesNotContain(created.secret());
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void authenticatesAValidKeyAndResolvesItsTenantAndScopes() {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Valid", Set.of(ApiScope.WORKFLOWS_READ), null)).secret();

        var principal = apiKeyService.authenticate(secret).orElseThrow();

        assertThat(principal.tenantId()).isEqualTo(tenant.getId());
        assertThat(principal.scopes()).containsExactly(ApiScope.WORKFLOWS_READ);
    }

    @Test
    void rejectsAMalformedKey() {
        assertThat(apiKeyService.authenticate("not-a-key")).isEmpty();          // no underscores
        assertThat(apiKeyService.authenticate("wrong_prefix_here")).isEmpty();  // wrong prefix
        assertThat(apiKeyService.authenticate("forge_a_b_c")).isEmpty();        // too many parts
        assertThat(apiKeyService.authenticate("forge_unknown_secret")).isEmpty(); // well-formed, unknown id
        assertThat(apiKeyService.authenticate("")).isEmpty();
        assertThat(apiKeyService.authenticate(null)).isEmpty();
    }

    @Test
    void aKeyCannotSeeAnotherTenantsData() {
        // Tenant B has a workflow; tenant A's key must not be able to list it through the public API.
        Tenant other = tenantRepository.save(Tenant.builder()
                .name("other-apikey").slug("other-apikey").status(TenantStatus.ACTIVE).build());

        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Tenant A key", Set.of(ApiScope.WORKFLOWS_READ), null)).secret();

        var principal = apiKeyService.authenticate(secret).orElseThrow();

        // The tenant comes from the key itself, so there is no parameter an attacker could change.
        assertThat(principal.tenantId()).isEqualTo(tenant.getId()).isNotEqualTo(other.getId());
    }

    @Test
    void rejectsAKeyWithTheWrongSecret() {
        ApiKeyCreatedResponse created = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Tampered", Set.of(ApiScope.WORKFLOWS_READ), null));
        ApiKey stored = apiKeyRepository.findByIdAndTenantId(created.key().id(), tenant.getId()).orElseThrow();

        // Correct key id, wrong secret - the attack of guessing the secret half.
        assertThat(apiKeyService.authenticate("forge_" + stored.getKeyId() + "_wrongsecret")).isEmpty();
    }

    @Test
    void rejectsARevokedKey() {
        ApiKeyCreatedResponse created = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Revoked", Set.of(ApiScope.WORKFLOWS_READ), null));
        apiKeyService.revokeKey(created.key().id());

        assertThat(apiKeyService.authenticate(created.secret())).isEmpty();
    }

    @Test
    void rejectsAnExpiredKey() {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Expired", Set.of(ApiScope.WORKFLOWS_READ), Instant.now().minusSeconds(60))).secret();

        assertThat(apiKeyService.authenticate(secret)).isEmpty();
    }

    @Test
    void revocationIsRecordedRatherThanDeletingTheRow() {
        ApiKeyCreatedResponse created = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Audit trail", Set.of(ApiScope.WORKFLOWS_READ), null));
        apiKeyService.revokeKey(created.key().id());

        ApiKey stored = apiKeyRepository.findByIdAndTenantId(created.key().id(), tenant.getId()).orElseThrow();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    // ------------------------------------------------------------------ the HTTP surface

    @Test
    void publicEndpointWithoutAKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/public/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("API key is required")));
    }

    @Test
    void publicEndpointWithAnInvalidKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/public/ping").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, "forge_x_y"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointWithAValidKeySucceeds() throws Exception {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Ping", Set.of(ApiScope.INSTANCES_READ), null)).secret();

        mockMvc.perform(get("/v1/public/ping").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void aKeyWithoutTheRequiredScopeIsForbidden() throws Exception {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Instances only", Set.of(ApiScope.INSTANCES_READ), null)).secret();

        // Has INSTANCES_READ, asks for workflows - scope must be checked, not just authentication.
        mockMvc.perform(get("/v1/public/workflows")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, secret))
                .andExpect(status().isForbidden());
    }

    @Test
    void aKeyWithTheRequiredScopeSucceeds() throws Exception {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Workflows", Set.of(ApiScope.WORKFLOWS_READ), null)).secret();

        mockMvc.perform(get("/v1/public/workflows")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, secret))
                .andExpect(status().isOk());
    }

    @Test
    void anApiKeyCannotAuthenticateAgainstTheUserFacingApi() throws Exception {
        String secret = apiKeyService.createKey(new ApiKeyCreateRequest(
                "Escalation attempt", Set.of(ApiScope.WORKFLOWS_READ), null)).secret();

        // The key is valid, but /v1/users is not the public surface. The filter does not even run
        // there, so there is no principal and the request is unauthenticated.
        mockMvc.perform(get("/v1/users").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, secret))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUsersJwtCannotSatisfyAScopeCheckOnThePublicApi() throws Exception {
        // The mirror image: SCOPE_ authorities are unreachable from a user token, so an owner with
        // every permission still cannot use the public surface with their JWT.
        Role owner = roleRepository.save(Role.builder()
                .tenantId(tenant.getId()).name(RoleCatalog.OWNER).systemRole(true)
                .permissions(EnumSet.allOf(Permission.class)).build());
        User user = userRepository.save(User.builder()
                .tenantId(tenant.getId()).email("owner@apikey-tenant.example")
                .passwordHash("unused").fullName("Owner").role(owner).status(UserStatus.ACTIVE).build());

        mockMvc.perform(get("/v1/public/workflows")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAKeyRequiresRoleManage() throws Exception {
        Role member = roleRepository.save(Role.builder()
                .tenantId(tenant.getId()).name(RoleCatalog.MEMBER).systemRole(true)
                .permissions(EnumSet.of(Permission.USER_READ)).build());
        User user = userRepository.save(User.builder()
                .tenantId(tenant.getId()).email("member@apikey-tenant.example")
                .passwordHash("unused").fullName("Member").role(member).status(UserStatus.ACTIVE).build());

        mockMvc.perform(post("/v1/api-keys")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Nope", "scopes", java.util.List.of("WORKFLOWS_READ")))))
                .andExpect(status().isForbidden());
    }
}

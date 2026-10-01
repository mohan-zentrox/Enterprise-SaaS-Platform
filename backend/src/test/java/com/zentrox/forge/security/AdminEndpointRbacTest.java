package com.zentrox.forge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.service.RoleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authorization coverage for the administration endpoints added for the previously
 * orphaned permissions (USER_*, ROLE_MANAGE, TENANT_*, AUDIT_LOG_READ). Exercised through the real
 * filter chain via MockMvc, in the same style as RbacEnforcementTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminEndpointRbacTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("admin-rbac-tenant").slug("admin-rbac-tenant").status(TenantStatus.ACTIVE).build());
    }

    // ------------------------------------------------------------------ user management

    @Test
    void invitingAUserWithoutUserInvitePermissionIsForbidden() throws Exception {
        String token = tokenFor("MEMBER", EnumSet.of(Permission.USER_READ));

        mockMvc.perform(post("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Should Be Rejected",
                                "email", "rejected@admin-rbac-tenant.example",
                                "initialPassword", "a-strong-password",
                                "roleName", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitingAUserWithUserInvitePermissionSucceeds() throws Exception {
        seedRole("MEMBER", EnumSet.of(Permission.USER_READ));
        String token = tokenFor(RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        mockMvc.perform(post("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Invited Person",
                                "email", "invited@admin-rbac-tenant.example",
                                "initialPassword", "a-strong-password",
                                "roleName", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("invited@admin-rbac-tenant.example"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void listingUsersRequiresUserRead() throws Exception {
        String token = tokenFor("RESTRICTED", EnumSet.of(Permission.WORKFLOW_INSTANCE_READ));

        mockMvc.perform(get("/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listedUsersArePaginatedWithAStableEnvelope() throws Exception {
        String token = tokenFor(RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        mockMvc.perform(get("/v1/users?page=0&size=10").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.hasNext").exists());
    }

    // ------------------------------------------------------------------ role management

    @Test
    void creatingARoleRequiresRoleManage() throws Exception {
        String token = tokenFor("MEMBER", EnumSet.of(Permission.USER_READ));

        mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "NOPE", "permissions", List.of("USER_READ")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPermissionNameIsRejectedAsBadRequest() throws Exception {
        String token = tokenFor(RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "TYPO", "permissions", List.of("NOT_A_REAL_PERMISSION")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grantingAPermissionTheCallerLacksIsForbiddenAndSaysWhich() throws Exception {
        // ADMIN holds everything except TENANT_SUSPEND, so it must not be able to mint a role that
        // has it. The message matters here: unlike a plain authorization failure, this 403 names
        // the over-reached permissions so the administrator can fix their request.
        Set<Permission> adminGrants = EnumSet.allOf(Permission.class);
        adminGrants.remove(Permission.TENANT_SUSPEND);
        String token = tokenFor(RoleCatalog.ADMIN, adminGrants);

        mockMvc.perform(post("/v1/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "ESCALATED", "permissions", List.of("TENANT_SUSPEND")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TENANT_SUSPEND")));
    }

    // ------------------------------------------------------------------ tenant administration

    @Test
    void readingTheCurrentTenantRequiresTenantRead() throws Exception {
        String token = tokenFor("RESTRICTED", EnumSet.of(Permission.WORKFLOW_INSTANCE_READ));

        mockMvc.perform(get("/v1/tenants/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void readingTheCurrentTenantReturnsTheCallersOwnTenant() throws Exception {
        String token = tokenFor(RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        mockMvc.perform(get("/v1/tenants/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("admin-rbac-tenant"));
    }

    @Test
    void tenantAdministrationEndpointsAreNotPubliclyReachable() throws Exception {
        // POST /v1/tenants is permitAll for self-signup; that must not extend to /v1/tenants/current.
        mockMvc.perform(get("/v1/tenants/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendingTheTenantRequiresTenantSuspend() throws Exception {
        // ADMIN holds everything except TENANT_SUSPEND - the one distinction between ADMIN and OWNER.
        Set<Permission> adminGrants = EnumSet.allOf(Permission.class);
        adminGrants.remove(Permission.TENANT_SUSPEND);
        String token = tokenFor(RoleCatalog.ADMIN, adminGrants);

        mockMvc.perform(post("/v1/tenants/current/suspend").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ audit log

    @Test
    void readingTheAuditLogRequiresAuditLogRead() throws Exception {
        String token = tokenFor("MEMBER", EnumSet.of(Permission.USER_READ));

        mockMvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogRecordsTheInviteThatJustHappened() throws Exception {
        seedRole("MEMBER", EnumSet.of(Permission.USER_READ));
        String token = tokenFor(RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        mockMvc.perform(post("/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Audited Invite",
                                "email", "audited@admin-rbac-tenant.example",
                                "initialPassword", "a-strong-password",
                                "roleName", "MEMBER"))))
                .andExpect(status().isCreated());

        // Closes the loop the platform previously could not: an audited write is now readable back.
        mockMvc.perform(get("/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_INVITE"))
                .andExpect(jsonPath("$.content[0].entityType").value("User"));
    }

    // ------------------------------------------------------------------ helpers

    private Role seedRole(String name, Set<Permission> permissions) {
        return roleRepository.save(Role.builder()
                .tenantId(tenant.getId())
                .name(name)
                .systemRole(true)
                .permissions(EnumSet.copyOf(permissions))
                .build());
    }

    /** Persists a role + user with the given grants and returns a real signed access token for them. */
    private String tokenFor(String roleName, Set<Permission> permissions) {
        Role role = seedRole(roleName, permissions);
        User user = userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email(roleName.toLowerCase() + "@admin-rbac-tenant.example")
                .passwordHash("unused")
                .fullName(roleName + " User")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
        return jwtService.generateAccessToken(user);
    }
}

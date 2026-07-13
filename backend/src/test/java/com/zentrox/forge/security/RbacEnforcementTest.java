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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that the {@code @PreAuthorize} guards on write endpoints (FRD
 * Section 4, RBAC) actually reject callers lacking the required permission, and
 * accept callers who have it - exercised through the real filter chain
 * (JwtAuthenticationFilter -> TenantFilterInterceptor -> @PreAuthorize) via MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacEnforcementTest {

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
                .name("rbac-test-tenant")
                .slug("rbac-test-tenant")
                .status(TenantStatus.ACTIVE)
                .build());
    }

    @Test
    void memberWithoutWorkflowDefinitionCreatePermissionGetsForbidden() throws Exception {
        Role memberRole = roleRepository.save(Role.builder()
                .tenantId(tenant.getId())
                .name("MEMBER")
                .systemRole(true)
                .permissions(EnumSet.of(Permission.WORKFLOW_DEFINITION_READ))
                .build());
        User member = userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email("member@rbac-test.example")
                .passwordHash("unused")
                .fullName("Member User")
                .role(memberRole)
                .status(UserStatus.ACTIVE)
                .build());

        String token = jwtService.generateAccessToken(member);

        mockMvc.perform(post("/v1/workflows/definitions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Should Be Rejected",
                                "states", java.util.List.of("DRAFT"),
                                "transitions", java.util.List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerWithWorkflowDefinitionCreatePermissionSucceeds() throws Exception {
        Role ownerRole = roleRepository.save(Role.builder()
                .tenantId(tenant.getId())
                .name("OWNER")
                .systemRole(true)
                .permissions(EnumSet.allOf(Permission.class))
                .build());
        User owner = userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email("owner@rbac-test.example")
                .passwordHash("unused")
                .fullName("Owner User")
                .role(ownerRole)
                .status(UserStatus.ACTIVE)
                .build());

        String token = jwtService.generateAccessToken(owner);

        mockMvc.perform(post("/v1/workflows/definitions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Allowed Workflow",
                                "states", java.util.List.of("DRAFT", "DONE"),
                                "transitions", java.util.List.of(Map.of("from", "DRAFT", "to", "DONE"))))))
                .andExpect(status().isCreated());
    }

    @Test
    void requestWithNoTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/workflows/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "No Auth",
                                "states", java.util.List.of("DRAFT"),
                                "transitions", java.util.List.of()))))
                .andExpect(status().isUnauthorized());
    }
}

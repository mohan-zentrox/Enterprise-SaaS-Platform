package com.zentrox.forge.service;

import com.zentrox.forge.dto.role.RoleResponse;
import com.zentrox.forge.dto.role.RoleWriteRequest;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.PrivilegeEscalationException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.CustomUserDetails;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the two invariants RoleManagementService exists to protect: system roles stay immutable,
 * and nobody can grant themselves authority they do not already hold.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleManagementServiceTest {

    @Autowired
    private RoleManagementService roleManagementService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    private Tenant tenant;
    private Role systemOwnerRole;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("roles-tenant").slug("roles-tenant").status(TenantStatus.ACTIVE).build());

        systemOwnerRole = roleRepository.save(Role.builder()
                .tenantId(tenant.getId())
                .name(RoleCatalog.OWNER)
                .systemRole(true)
                .permissions(EnumSet.allOf(Permission.class))
                .build());

        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsACustomRoleWithTheRequestedPermissions() {
        authenticateAs(EnumSet.allOf(Permission.class));

        RoleResponse created = roleManagementService.createRole(new RoleWriteRequest(
                "AUDITOR", Set.of(Permission.AUDIT_LOG_READ, Permission.USER_READ)));

        assertThat(created.systemRole()).isFalse();
        assertThat(created.permissions()).containsExactly("AUDIT_LOG_READ", "USER_READ");
    }

    @Test
    void refusesToReuseASystemRoleName() {
        authenticateAs(EnumSet.allOf(Permission.class));

        assertThatThrownBy(() -> roleManagementService.createRole(
                new RoleWriteRequest("owner", Set.of(Permission.USER_READ))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void refusesToModifyASystemRole() {
        authenticateAs(EnumSet.allOf(Permission.class));

        assertThatThrownBy(() -> roleManagementService.updateRole(
                systemOwnerRole.getId(), new RoleWriteRequest("OWNER", Set.of(Permission.USER_READ))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be modified");
    }

    @Test
    void refusesToDeleteASystemRole() {
        authenticateAs(EnumSet.allOf(Permission.class));

        assertThatThrownBy(() -> roleManagementService.deleteRole(systemOwnerRole.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void refusesToGrantAPermissionTheCallerDoesNotHold() {
        // An administrator who cannot suspend the tenant must not be able to create a role that can.
        authenticateAs(EnumSet.of(Permission.ROLE_MANAGE, Permission.USER_READ));

        assertThatThrownBy(() -> roleManagementService.createRole(
                new RoleWriteRequest("ESCALATED", Set.of(Permission.TENANT_SUSPEND))))
                .isInstanceOf(PrivilegeEscalationException.class)
                .hasMessageContaining("TENANT_SUSPEND");
    }

    @Test
    void refusesToDeleteARoleThatStillHasMembers() {
        authenticateAs(EnumSet.allOf(Permission.class));
        RoleResponse custom = roleManagementService.createRole(
                new RoleWriteRequest("CONTRACTOR", Set.of(Permission.USER_READ)));

        Role stored = roleRepository.findByIdAndTenantId(custom.id(), tenant.getId()).orElseThrow();
        userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email("contractor@roles-tenant.example")
                .passwordHash("unused")
                .fullName("Contractor")
                .role(stored)
                .status(UserStatus.ACTIVE)
                .build());

        assertThatThrownBy(() -> roleManagementService.deleteRole(custom.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assigned to 1 user");
    }

    @Test
    void deletesAnUnusedCustomRole() {
        authenticateAs(EnumSet.allOf(Permission.class));
        RoleResponse custom = roleManagementService.createRole(
                new RoleWriteRequest("TEMPORARY", Set.of(Permission.USER_READ)));

        roleManagementService.deleteRole(custom.id());

        assertThat(roleRepository.findByIdAndTenantId(custom.id(), tenant.getId())).isEmpty();
    }

    @Test
    void permissionCatalogExposesEveryPermission() {
        assertThat(roleManagementService.listPermissionCatalog())
                .hasSize(Permission.values().length)
                .contains("AUDIT_LOG_READ", "TENANT_SUSPEND", "WORKFLOW_INSTANCE_TRANSITION");
    }

    private void authenticateAs(Set<Permission> permissions) {
        Set<String> names = permissions.stream().map(Enum::name).collect(Collectors.toSet());
        CustomUserDetails principal = new CustomUserDetails(
                UUID.randomUUID(), tenant.getId(), "admin@roles-tenant.example", RoleCatalog.OWNER, names);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

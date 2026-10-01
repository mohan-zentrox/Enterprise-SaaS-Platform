package com.zentrox.forge.service;

import com.zentrox.forge.dto.UserResponse;
import com.zentrox.forge.dto.user.UserCreateRequest;
import com.zentrox.forge.dto.user.UserUpdateRequest;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The isolation test for the user-administration module, mirroring TenantIsolationTest: a new
 * tenant-scoped surface does not get to rely on "the repository probably filters" - per
 * docs/TEAM.md, a tenant-scoped feature ships with a test proving it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserManagementServiceTest {

    @Autowired
    private UserManagementService userManagementService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private User tenantBsUser;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(newTenant("users-tenant-a"));
        tenantB = tenantRepository.save(newTenant("users-tenant-b"));

        seedRole(tenantA.getId(), RoleCatalog.OWNER, EnumSet.allOf(Permission.class));
        seedRole(tenantA.getId(), RoleCatalog.MEMBER, EnumSet.of(Permission.USER_READ));
        Role ownerB = seedRole(tenantB.getId(), RoleCatalog.OWNER, EnumSet.allOf(Permission.class));

        tenantBsUser = userRepository.save(User.builder()
                .tenantId(tenantB.getId())
                .email("owner@tenant-b.example")
                .passwordHash("unused")
                .fullName("Tenant B Owner")
                .role(ownerB)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsUserInTheCallersTenantOnly() {
        TenantContext.setTenantId(tenantA.getId());

        UserResponse created = userManagementService.createUser(new UserCreateRequest(
                "Newly Invited", "invited@tenant-a.example", "a-strong-password", RoleCatalog.MEMBER));

        assertThat(created.email()).isEqualTo("invited@tenant-a.example");
        assertThat(created.role()).isEqualTo(RoleCatalog.MEMBER);
        assertThat(userRepository.findByIdAndTenantId(created.id(), tenantA.getId())).isPresent();
        // The decisive assertion: the row is not reachable from the other tenant.
        assertThat(userRepository.findByIdAndTenantId(created.id(), tenantB.getId())).isEmpty();
    }

    @Test
    void cannotFetchAnotherTenantsUserByGuessingItsId() {
        TenantContext.setTenantId(tenantA.getId());

        assertThatThrownBy(() -> userManagementService.getUser(tenantBsUser.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotUpdateAnotherTenantsUser() {
        TenantContext.setTenantId(tenantA.getId());

        assertThatThrownBy(() -> userManagementService.updateUser(
                tenantBsUser.getId(), new UserUpdateRequest("Hijacked", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotDeactivateAnotherTenantsUser() {
        TenantContext.setTenantId(tenantA.getId());

        assertThatThrownBy(() -> userManagementService.deactivateUser(tenantBsUser.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listNeverIncludesAnotherTenantsUsers() {
        TenantContext.setTenantId(tenantA.getId());
        userManagementService.createUser(new UserCreateRequest(
                "Tenant A Person", "person@tenant-a.example", "a-strong-password", RoleCatalog.MEMBER));

        var page = userManagementService.listUsers(PageRequest.of(0, 50));

        assertThat(page.content()).extracting(UserResponse::id).doesNotContain(tenantBsUser.getId());
        assertThat(page.content()).extracting(UserResponse::email).containsExactly("person@tenant-a.example");
    }

    @Test
    void rejectsDuplicateEmailWithinTheSameTenant() {
        TenantContext.setTenantId(tenantA.getId());
        userManagementService.createUser(new UserCreateRequest(
                "First", "dupe@tenant-a.example", "a-strong-password", RoleCatalog.MEMBER));

        assertThatThrownBy(() -> userManagementService.createUser(new UserCreateRequest(
                "Second", "dupe@tenant-a.example", "a-strong-password", RoleCatalog.MEMBER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void allowsTheSameEmailInTwoDifferentTenants() {
        TenantContext.setTenantId(tenantA.getId());
        userManagementService.createUser(new UserCreateRequest(
                "In A", "shared@example.com", "a-strong-password", RoleCatalog.MEMBER));

        TenantContext.setTenantId(tenantB.getId());
        UserResponse inB = userManagementService.createUser(new UserCreateRequest(
                "In B", "shared@example.com", "a-strong-password", RoleCatalog.OWNER));

        assertThat(inB.id()).isNotNull();
    }

    @Test
    void storesThePasswordAsAHashNotPlaintext() {
        TenantContext.setTenantId(tenantA.getId());
        UserResponse created = userManagementService.createUser(new UserCreateRequest(
                "Hashed", "hashed@tenant-a.example", "a-strong-password", RoleCatalog.MEMBER));

        User stored = userRepository.findByIdAndTenantId(created.id(), tenantA.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("a-strong-password").startsWith("{bcrypt}");
    }

    @Test
    void rejectsARoleThatBelongsToAnotherTenant() {
        TenantContext.setTenantId(tenantA.getId());

        // "OWNER" exists in both tenants; this asserts resolution is tenant-scoped rather than
        // by-name-globally, by using a role name that only tenant B has.
        seedRole(tenantB.getId(), "TENANT_B_ONLY", EnumSet.of(Permission.USER_READ));

        assertThatThrownBy(() -> userManagementService.createUser(new UserCreateRequest(
                "Nope", "nope@tenant-a.example", "a-strong-password", "TENANT_B_ONLY")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void refusesToDeactivateTheLastActiveOwner() {
        TenantContext.setTenantId(tenantB.getId());

        assertThatThrownBy(() -> userManagementService.deactivateUser(tenantBsUser.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last active owner");
    }

    private Tenant newTenant(String slug) {
        return Tenant.builder().name(slug).slug(slug).status(TenantStatus.ACTIVE).build();
    }

    private Role seedRole(UUID tenantId, String name, EnumSet<Permission> permissions) {
        return roleRepository.save(Role.builder()
                .tenantId(tenantId)
                .name(name)
                .systemRole(true)
                .permissions(permissions)
                .build());
    }
}

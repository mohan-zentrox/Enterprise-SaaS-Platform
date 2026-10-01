package com.zentrox.forge.service;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.dto.role.RoleResponse;
import com.zentrox.forge.dto.role.RoleWriteRequest;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.exception.PrivilegeEscalationException;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FRD Section 4 (RBAC) - custom per-tenant roles, behind ROLE_MANAGE. Previously the three system
 * roles seeded at tenant creation were the only roles that could ever exist, and ROLE_MANAGE was
 * an unreachable permission.
 *
 * Two invariants are enforced here rather than left to callers:
 *
 * <ol>
 *   <li><b>System roles are immutable.</b> OWNER/ADMIN/MEMBER are the seeding contract that
 *       AuthService and TenantService depend on; renaming or re-granting them would break tenant
 *       creation and leave existing users with silently different authority.</li>
 *   <li><b>No privilege escalation.</b> A caller may only grant permissions they themselves hold.
 *       Without this, any ROLE_MANAGE holder could mint a role with TENANT_SUSPEND, assign it to
 *       themselves, and become more powerful than the owner who invited them.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public List<RoleResponse> listRoles() {
        UUID tenantId = TenantContext.requireTenantId();
        return roleRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .map(RoleResponse::from)
                .toList();
    }

    public RoleResponse getRole(UUID id) {
        return RoleResponse.from(requireRole(id));
    }

    /** The full catalog, so an administration UI can render the available grants. */
    public List<String> listPermissionCatalog() {
        return EnumSet.allOf(Permission.class).stream().map(Enum::name).sorted().toList();
    }

    @Audited(action = "ROLE_CREATE", entityType = "Role")
    @Transactional
    public RoleResponse createRole(RoleWriteRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String name = request.name().trim();

        if (RoleCatalog.isSystemRoleName(name)) {
            throw new ConflictException("'" + name + "' is a reserved system role name");
        }
        if (roleRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ConflictException("A role named '" + name + "' already exists in this organization");
        }
        requireCallerHoldsAll(request.permissions());

        Role role = Role.builder()
                .tenantId(tenantId)
                .name(name)
                .systemRole(false)
                .permissions(toEnumSet(request.permissions()))
                .build();

        return RoleResponse.from(roleRepository.save(role));
    }

    @Audited(action = "ROLE_UPDATE", entityType = "Role")
    @Transactional
    public RoleResponse updateRole(UUID id, RoleWriteRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = requireRole(id);

        if (role.isSystemRole()) {
            throw new ConflictException(
                    "System role '" + role.getName() + "' cannot be modified. Create a custom role instead.");
        }

        String name = request.name().trim();
        if (RoleCatalog.isSystemRoleName(name)) {
            throw new ConflictException("'" + name + "' is a reserved system role name");
        }
        if (!name.equals(role.getName()) && roleRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ConflictException("A role named '" + name + "' already exists in this organization");
        }
        requireCallerHoldsAll(request.permissions());

        role.setName(name);
        role.setPermissions(toEnumSet(request.permissions()));
        return RoleResponse.from(roleRepository.save(role));
    }

    @Audited(action = "ROLE_DELETE", entityType = "Role")
    @Transactional
    public void deleteRole(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        Role role = requireRole(id);

        if (role.isSystemRole()) {
            throw new ConflictException("System role '" + role.getName() + "' cannot be deleted");
        }

        long members = userRepository.countByTenantIdAndRoleId(tenantId, role.getId());
        if (members > 0) {
            throw new ConflictException("This role is assigned to " + members
                    + " user(s). Reassign them before deleting the role.");
        }

        roleRepository.deleteByIdAndTenantId(id, tenantId);
    }

    /**
     * Rejects an attempt to grant a permission the caller does not hold. Compared against the
     * authorities on the access token, which are exactly the caller's resolved permission set.
     */
    private void requireCallerHoldsAll(Set<Permission> requested) {
        Set<String> callerPermissions = SecurityUtils.currentUser()
                .map(principal -> principal.getPermissions())
                .orElseThrow(() -> new AccessDeniedException("No authenticated principal"));

        Set<String> escalations = safe(requested).stream()
                .map(Enum::name)
                .filter(name -> !callerPermissions.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!escalations.isEmpty()) {
            throw new PrivilegeEscalationException(
                    "You cannot grant permissions you do not hold yourself: " + String.join(", ", escalations));
        }
    }

    private Set<Permission> safe(Set<Permission> permissions) {
        return permissions == null ? Set.of() : permissions;
    }

    /** EnumSet.copyOf throws on an empty non-EnumSet collection, so an empty grant needs this path. */
    private EnumSet<Permission> toEnumSet(Set<Permission> permissions) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(safe(permissions));
        return result;
    }

    private Role requireRole(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Role not found: " + id));
    }
}

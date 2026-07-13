package com.zentrox.forge.service;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.dto.TenantCreateRequest;
import com.zentrox.forge.dto.TenantCreateResponse;
import com.zentrox.forge.dto.TenantResponse;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * FRD Section 3 (Multi-Tenancy): tenant self-signup. {@link #createTenant} is the
 * single transactional entry point that creates a Tenant, seeds its three system
 * roles (OWNER/ADMIN/MEMBER) with their permission grants, and creates the first
 * user as OWNER - all-or-nothing.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Audited(action = "TENANT_CREATE", entityType = "Tenant")
    @Transactional
    public TenantCreateResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ConflictException("A tenant with slug '" + request.slug() + "' already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(request.name())
                .slug(request.slug())
                .status(TenantStatus.ACTIVE)
                .build();
        tenant = tenantRepository.save(tenant);

        Map<String, Role> rolesByName = seedSystemRoles(tenant.getId());

        User owner = User.builder()
                .tenantId(tenant.getId())
                .email(request.ownerEmail())
                .passwordHash(passwordEncoder.encode(request.ownerPassword()))
                .fullName(request.ownerFullName())
                .role(rolesByName.get(RoleCatalog.OWNER))
                .status(UserStatus.ACTIVE)
                .build();
        owner = userRepository.save(owner);

        // The audit aspect reads TenantContext to attribute this action; there is no
        // authenticated caller yet (tenant self-signup precedes login), so we bind it
        // explicitly for the duration of this request. TenantFilterInterceptor clears
        // it in its normal afterCompletion hook regardless of how it was set.
        TenantContext.setTenantId(tenant.getId());

        return new TenantCreateResponse(TenantResponse.from(tenant), owner.getId(), owner.getEmail());
    }

    private Map<String, Role> seedSystemRoles(java.util.UUID tenantId) {
        Map<String, Role> result = new HashMap<>();
        for (Map.Entry<String, Set<Permission>> entry : RoleCatalog.systemRoleGrants().entrySet()) {
            Role role = Role.builder()
                    .tenantId(tenantId)
                    .name(entry.getKey())
                    .systemRole(true)
                    .permissions(new java.util.HashSet<>(entry.getValue()))
                    .build();
            result.put(entry.getKey(), roleRepository.save(role));
        }
        return result;
    }
}

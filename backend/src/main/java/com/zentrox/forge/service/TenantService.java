package com.zentrox.forge.service;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.billing.SubscriptionPlan;
import com.zentrox.forge.billing.SubscriptionStatus;
import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.repository.tenant.SubscriptionRepository;
import com.zentrox.forge.dto.TenantCreateRequest;
import com.zentrox.forge.dto.TenantCreateResponse;
import com.zentrox.forge.dto.TenantResponse;
import com.zentrox.forge.dto.TenantUpdateRequest;
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
    private final SubscriptionRepository subscriptionRepository;

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

        // Every tenant needs a subscription row from the moment it exists: EntitlementService
        // tolerates a missing one by assuming FREE, but only so a bug degrades gracefully - the
        // row should always be here. V4 backfills tenants created before billing existed.
        subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId())
                .plan(SubscriptionPlan.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .build());

        // The audit aspect reads TenantContext to attribute this action; there is no
        // authenticated caller yet (tenant self-signup precedes login), so we bind it
        // explicitly for the duration of this request. TenantFilterInterceptor clears
        // it in its normal afterCompletion hook regardless of how it was set.
        TenantContext.setTenantId(tenant.getId());

        return new TenantCreateResponse(TenantResponse.from(tenant), owner.getId(), owner.getEmail());
    }

    // ------------------------------------------------------ tenant administration (FRD Section 3)
    // These three operations back TENANT_READ / TENANT_UPDATE / TENANT_SUSPEND, which were in the
    // permission catalog from the start with no endpoint behind them. The tenant is always the
    // caller's own, resolved from TenantContext - there is no endpoint that takes a tenant id, so
    // no administrator can read or modify a tenant other than their own.

    public TenantResponse getCurrentTenant() {
        return TenantResponse.from(requireCurrentTenant());
    }

    @Audited(action = "TENANT_UPDATE", entityType = "Tenant")
    @Transactional
    public TenantResponse renameCurrentTenant(TenantUpdateRequest request) {
        Tenant tenant = requireCurrentTenant();
        tenant.setName(request.name());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    /**
     * Suspending a tenant blocks new logins and token refreshes for every user in it
     * (see AuthService#login and #requireActiveTenant). Outstanding access tokens remain valid
     * until they expire, which is the same 15-minute window that applies to user deactivation.
     */
    @Audited(action = "TENANT_SUSPEND", entityType = "Tenant")
    @Transactional
    public TenantResponse suspendCurrentTenant() {
        Tenant tenant = requireCurrentTenant();
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new ConflictException("This organization is already suspended");
        }
        tenant.setStatus(TenantStatus.SUSPENDED);
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    /**
     * Reactivation is guarded by TENANT_SUSPEND as well. Note the practical consequence of
     * suspending your own tenant: nobody can log in to undo it, so recovery requires an operator
     * with database access. The API says so rather than pretending otherwise.
     */
    @Audited(action = "TENANT_ACTIVATE", entityType = "Tenant")
    @Transactional
    public TenantResponse activateCurrentTenant() {
        Tenant tenant = requireCurrentTenant();
        tenant.setStatus(TenantStatus.ACTIVE);
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    private Tenant requireCurrentTenant() {
        java.util.UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + tenantId));
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

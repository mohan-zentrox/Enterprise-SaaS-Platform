package com.zentrox.forge.service;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.billing.RequiresEntitlement;
import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.dto.UserResponse;
import com.zentrox.forge.dto.user.UserCreateRequest;
import com.zentrox.forge.dto.user.UserUpdateRequest;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.notification.event.UserInvitedEvent;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * FRD Section 4 (User Administration) - the endpoints behind USER_INVITE / USER_READ /
 * USER_UPDATE / USER_DEACTIVATE. These permissions existed in the catalog from the start with no
 * controller implementing them, which made the platform look more complete than it was.
 *
 * Every lookup goes through the tenant-scoped repository with the tenant taken from
 * {@link TenantContext} - never from the request - so an administrator can only ever see and
 * modify users inside their own tenant.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Audited(action = "USER_INVITE", entityType = "User")
    @RequiresEntitlement(UsageMetric.SEATS)
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (userRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new ConflictException("A user with this email already exists in this organization");
        }

        Role role = requireRole(tenantId, request.roleName());

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.initialPassword()))
                .fullName(request.fullName())
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);

        // Published rather than calling NotificationService directly: the listener fires after this
        // transaction commits, so nobody is welcomed to an account that rolled back.
        String organizationName = tenantRepository.findById(tenantId)
                .map(t -> t.getName())
                .orElse("your organization");
        eventPublisher.publishEvent(new UserInvitedEvent(
                tenantId, saved.getId(), saved.getEmail(), saved.getFullName(), role.getName(), organizationName));

        return UserResponse.from(saved);
    }

    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return PageResponse.from(
                userRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable),
                UserResponse::from);
    }

    public UserResponse getUser(UUID id) {
        return UserResponse.from(requireUser(id));
    }

    @Audited(action = "USER_UPDATE", entityType = "User")
    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        User user = requireUser(id);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }

        if (request.roleName() != null && !request.roleName().isBlank()) {
            Role newRole = requireRole(tenantId, request.roleName());
            // Re-roling the last active OWNER would leave the tenant with nobody able to administer
            // it, including nobody able to undo this change.
            if (isLastActiveOwner(tenantId, user) && !newRole.getName().equals(RoleCatalog.OWNER)) {
                throw new ConflictException(
                        "This is the last active owner of the organization; promote another owner first");
            }
            user.setRole(newRole);
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Audited(action = "USER_DEACTIVATE", entityType = "User")
    @Transactional
    public UserResponse deactivateUser(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        User user = requireUser(id);

        SecurityUtils.currentUserId().ifPresent(callerId -> {
            if (callerId.equals(user.getId())) {
                throw new ConflictException("You cannot deactivate your own account");
            }
        });

        if (isLastActiveOwner(tenantId, user)) {
            throw new ConflictException(
                    "This is the last active owner of the organization; promote another owner first");
        }

        user.setStatus(UserStatus.DISABLED);
        return UserResponse.from(userRepository.save(user));
    }

    @Audited(action = "USER_ACTIVATE", entityType = "User")
    @Transactional
    public UserResponse activateUser(UUID id) {
        User user = requireUser(id);
        user.setStatus(UserStatus.ACTIVE);
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Deactivating or demoting a user does not invalidate their outstanding access token, which
     * stays valid until it expires (15 minutes by default). Their refresh tokens are the
     * long-lived credential, and {@code AuthService#refresh} already re-checks user status, so the
     * session cannot be extended past that window. Immediate revocation would need a per-user
     * token generation counter - see docs/ARCHITECTURE.md "Hardening backlog".
     */
    private boolean isLastActiveOwner(UUID tenantId, User user) {
        if (!RoleCatalog.OWNER.equals(user.getRole().getName()) || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        return userRepository.countByTenantIdAndRoleIdAndStatus(
                tenantId, user.getRole().getId(), UserStatus.ACTIVE) <= 1;
    }

    private User requireUser(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private Role requireRole(UUID tenantId, String roleName) {
        return roleRepository.findByTenantIdAndName(tenantId, roleName)
                .orElseThrow(() -> new NotFoundException("Unknown role for this organization: " + roleName));
    }
}

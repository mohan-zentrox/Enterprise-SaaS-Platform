package com.zentrox.forge.service;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.dto.AuthResponse;
import com.zentrox.forge.dto.LoginRequest;
import com.zentrox.forge.dto.RegisterRequest;
import com.zentrox.forge.dto.UserResponse;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.InvalidCredentialsException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.exception.SelfRegistrationDisabledException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.JwtProperties;
import com.zentrox.forge.security.JwtService;
import com.zentrox.forge.security.RegistrationProperties;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FRD Section 4 (AuthN): register / login / refresh / logout. Access tokens are short-lived
 * JWTs (tenant_id + role + permissions claims); refresh tokens are opaque and delegated to
 * {@link RefreshTokenService} (Redis-backed, revocable, rotated on every use).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final RegistrationProperties registrationProperties;

    @Audited(action = "USER_REGISTER", entityType = "User")
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!registrationProperties.selfServiceEnabled()) {
            throw new SelfRegistrationDisabledException(
                    "Public self-registration is disabled. Ask an administrator of this organization to "
                            + "invite you (POST /v1/users, permission USER_INVITE).");
        }

        Tenant tenant = requireActiveTenant(request.tenantSlug());

        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), request.email())) {
            throw new ConflictException("A user with this email already exists in this tenant");
        }

        // This endpoint is unauthenticated, so TenantFilterInterceptor bound no tenant and the
        // AuditAspect would silently skip the USER_REGISTER entry. Bind it explicitly - the same
        // approach TenantService#createTenant takes for tenant self-signup.
        TenantContext.setTenantId(tenant.getId());

        var memberRole = roleRepository.findByTenantIdAndName(tenant.getId(), RoleCatalog.MEMBER)
                .orElseThrow(() -> new IllegalStateException(
                        "MEMBER system role missing for tenant " + tenant.getId() + " - seeding invariant violated"));

        User user = User.builder()
                .tenantId(tenant.getId())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(memberRole)
                .status(UserStatus.ACTIVE)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid tenant, email, or password"));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new InvalidCredentialsException("This tenant is suspended");
        }

        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid tenant, email, or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("This account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid tenant, email, or password");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(String presentedRefreshToken) {
        var owner = refreshTokenService.resolve(presentedRefreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token is invalid, expired, or revoked"));

        User user = userRepository.findByIdAndTenantId(owner.userId(), owner.tenantId())
                .orElseThrow(() -> new NotFoundException("User no longer exists"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenService.revoke(presentedRefreshToken);
            throw new InvalidCredentialsException("This account is disabled");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.rotate(presentedRefreshToken, owner.tenantId(), owner.userId());
        return AuthResponse.of(accessToken, newRefreshToken, jwtProperties.accessTokenTtlMinutes() * 60);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * FRD-13.2 - the termination point for SSO logins.
     *
     * Exists so OidcLoginService issues tokens through exactly the same path as password login,
     * rather than growing a parallel session mechanism. Password verification is the only step that
     * is skipped: the identity provider has already established who the user is.
     *
     * Status is re-checked here rather than trusted from the caller, so this cannot become a way to
     * mint tokens for a disabled account.
     */
    @Transactional
    public AuthResponse issueTokensForSsoLogin(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("This account is disabled");
        }
        return issueTokenPair(user);
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getTenantId(), user.getId());
        return AuthResponse.of(accessToken, refreshToken, jwtProperties.accessTokenTtlMinutes() * 60);
    }

    private Tenant requireActiveTenant(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Unknown tenant: " + slug));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new ConflictException("This tenant is suspended and not accepting new users");
        }
        return tenant;
    }
}

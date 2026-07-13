package com.zentrox.forge.security;

import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test - no Spring context, no DB, no Redis. Proves the access token
 * round-trips its tenant_id/role/permissions claims correctly and that tampering
 * is rejected.
 */
class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "unit-test-secret-key-please-be-at-least-32-bytes-long", 15, "forge-test");
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void generatesAccessTokenCarryingTenantRoleAndPermissions() {
        UUID tenantId = UUID.randomUUID();
        User user = testUser(tenantId, "OWNER", Set.of(Permission.WORKFLOW_DEFINITION_CREATE, Permission.TENANT_READ));

        String token = jwtService.generateAccessToken(user);
        CustomUserDetails details = jwtService.toUserDetails(jwtService.parseAndValidate(token));

        assertThat(details.getUserId()).isEqualTo(user.getId());
        assertThat(details.getTenantId()).isEqualTo(tenantId);
        assertThat(details.getRoleName()).isEqualTo("OWNER");
        assertThat(details.getPermissions()).containsExactlyInAnyOrder(
                "WORKFLOW_DEFINITION_CREATE", "TENANT_READ");
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        User user = testUser(UUID.randomUUID(), "MEMBER", Set.of());
        String token = jwtService.generateAccessToken(user);

        JwtService attackerService = new JwtService(
                new JwtProperties("a-completely-different-secret-value-32bytes!", 15, "forge-test"));

        assertThatThrownBy(() -> attackerService.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    private User testUser(UUID tenantId, String roleName, Set<Permission> permissions) {
        Role role = Role.builder()
                .tenantId(tenantId)
                .name(roleName)
                .systemRole(true)
                .permissions(permissions)
                .build();
        role.setId(UUID.randomUUID());

        User user = User.builder()
                .tenantId(tenantId)
                .email("user@example.com")
                .passwordHash("irrelevant")
                .fullName("Test User")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}

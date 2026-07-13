package com.zentrox.forge.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication principal resolved from a validated JWT access token (see
 * JwtAuthenticationFilter). Authorities are the permission names embedded in the
 * token so {@code @PreAuthorize("hasAuthority('...')")} checks never hit the DB.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String roleName;
    private final Set<String> permissions;

    public CustomUserDetails(UUID userId, UUID tenantId, String email, String roleName, Set<String> permissions) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roleName = roleName;
        this.permissions = permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        // Never populated post-authentication; the JWT itself is the credential.
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}

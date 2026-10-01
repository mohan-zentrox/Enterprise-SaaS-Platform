package com.zentrox.forge.publicapi;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The authenticated principal for an API-key request (FRD-12.2).
 *
 * Deliberately NOT a {@code CustomUserDetails} and not a {@code UserDetails} at all: an API key is
 * not a person. Reusing the user principal would silently give an integration a user's identity in
 * the audit trail, and would make it easy to write authorization that cannot tell the two apart.
 *
 * Authorities are prefixed {@code SCOPE_} so no {@code hasAuthority('USER_READ')} check on the
 * authenticated API can ever be satisfied by an API key, however it is scoped.
 */
public record ApiKeyPrincipal(UUID apiKeyId, UUID tenantId, String name, Set<ApiScope> scopes) {

    public static final String AUTHORITY_PREFIX = "SCOPE_";

    public Collection<? extends GrantedAuthority> authorities() {
        return scopes.stream()
                .map(scope -> new SimpleGrantedAuthority(AUTHORITY_PREFIX + scope.name()))
                .collect(Collectors.toSet());
    }

    public boolean hasScope(ApiScope scope) {
        return scopes.contains(scope);
    }
}

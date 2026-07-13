package com.zentrox.forge.publicapi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SCAFFOLD ONLY - FRD Section 12 (Public API-Key-Authenticated Endpoints).
 *
 * TODO(FRD-12.1): Define an `api_keys` table (tenant_id, hashed_key, name, scopes,
 *   created_at, last_used_at, revoked_at). Never store raw keys - hash with the same
 *   rigor as passwords (see SecurityConfig#passwordEncoder) or a fast keyed hash (HMAC)
 *   since API keys are compared on every request.
 * TODO(FRD-12.2): Parse an `X-Api-Key` header here, resolve it to a tenant + scope set,
 *   and populate SecurityContext with a principal distinct from CustomUserDetails (an
 *   API key is not a user) so downstream @PreAuthorize checks still work.
 * TODO(FRD-12.3): Register this filter only for the /v1/public/** path (see
 *   SecurityConfig - that prefix is already permitAll'd at the HttpSecurity layer
 *   pending this filter taking over authentication for it).
 * TODO(FRD-12.4): Rate-limit by API key using Redis (see docs/ARCHITECTURE.md
 *   "Cross-cutting infra" - Redis is already wired for refresh tokens).
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        // TODO(FRD-12.2): not yet implemented - currently a pass-through no-op.
        filterChain.doFilter(request, response);
    }
}

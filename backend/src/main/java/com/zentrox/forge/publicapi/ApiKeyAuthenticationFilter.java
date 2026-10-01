package com.zentrox.forge.publicapi;

import com.zentrox.forge.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * FRD-12.2 - authenticates {@code /v1/public/**} requests from an {@code X-Api-Key} header.
 *
 * <p>Several things here are deliberate:
 *
 * <ul>
 *   <li><b>Scoped to /v1/public/** only</b> via {@link #shouldNotFilter}. An API key must never be
 *       usable against the authenticated surface, where authorization is written in terms of user
 *       permissions.</li>
 *   <li><b>Rejects rather than passes through.</b> Unlike JwtAuthenticationFilter - which leaves the
 *       decision to Spring Security's rules - this filter answers 401 itself, because the whole
 *       prefix is {@code permitAll} at the HttpSecurity layer and there is no later gate to fall
 *       back on. A pass-through here would leave the public API unauthenticated, which was exactly
 *       the state of the original scaffold.</li>
 *   <li><b>Binds TenantContext directly.</b> TenantFilterInterceptor resolves the tenant from a
 *       CustomUserDetails principal, which an API key is not; without this, every tenant-scoped
 *       query on the public API would fail for want of a bound tenant.</li>
 *   <li><b>Clears TenantContext in a finally block.</b> The thread returns to the container's pool,
 *       and a leaked tenant id would be read by whatever unrelated request lands on it next.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Api-Key";
    private static final String PUBLIC_PREFIX = "/v1/public";

    private final ApiKeyService apiKeyService;
    private final ApiKeyRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String presentedKey = request.getHeader(API_KEY_HEADER);
        if (presentedKey == null || presentedKey.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "An API key is required. Send it in the " + API_KEY_HEADER + " header.", request);
            return;
        }

        Optional<ApiKeyPrincipal> authenticated = apiKeyService.authenticate(presentedKey);
        if (authenticated.isEmpty()) {
            // One message for unknown, revoked, expired and malformed alike - distinguishing them
            // would confirm to a prober which key ids exist.
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key", request);
            return;
        }

        ApiKeyPrincipal principal = authenticated.get();

        if (!rateLimiter.tryConsume(principal.apiKeyId())) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "Rate limit exceeded: at most "
                    + rateLimiter.limitPerMinute() + " requests per minute per API key.", request);
            return;
        }

        try {
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.authorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            TenantContext.setTenantId(principal.tenantId());

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /** Writes the same ErrorResponse shape the rest of the API uses (see docs/API.md). */
    private void reject(HttpServletResponse response, int status, String message, HttpServletRequest request)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}".formatted(
                        status,
                        status == 429 ? "Too Many Requests" : "Unauthorized",
                        message,
                        request.getRequestURI()));
    }
}

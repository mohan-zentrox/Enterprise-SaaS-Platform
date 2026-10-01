package com.zentrox.forge.config;

import com.zentrox.forge.security.CorsProperties;
import com.zentrox.forge.publicapi.ApiKeyAuthenticationFilter;
import com.zentrox.forge.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * Stateless JWT-based security. No HTTP session is created (refresh tokens live in
 * Redis and are presented explicitly to POST /v1/auth/refresh - see RefreshTokenService).
 *
 * Endpoint policy:
 *  - POST /v1/tenants           : public (tenant self-signup creates the first Owner)
 *  - POST /v1/auth/register|login|refresh : public
 *  - POST /v1/auth/logout       : authenticated (needs the caller's own refresh token)
 *  - everything else            : authenticated, further narrowed by @PreAuthorize on
 *                                  each write endpoint (see controller package).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API, not cookie/session based
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // CORS preflight requests carry no Authorization header by design; without
                        // this they would be rejected by the authenticated() rule below before ever
                        // reaching the CorsFilter's actual allow/deny decision.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // POST only: tenant self-signup is public, but the administration endpoints
                        // under /v1/tenants/current must fall through to authenticated() below.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/v1/tenants").permitAll()
                        .requestMatchers("/v1/auth/register", "/v1/auth/login", "/v1/auth/refresh").permitAll()
                        .requestMatchers("/v1/api-docs/**", "/v1/swagger-ui/**", "/v1/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Authenticated by HMAC signature, not by a token - see BillingWebhookController.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/v1/webhooks/**").permitAll()
                        // permitAll at this layer because ApiKeyAuthenticationFilter is the real gate for
                        // this prefix - it authenticates the X-Api-Key header and answers 401 itself.
                        .requestMatchers("/v1/public/**").permitAll()
                        .requestMatchers("/v1/sso/**").permitAll() // SSO callback scaffold, see sso package (FRD-13.4)
                        .anyRequest().authenticated())
                // Without this, Spring Security's default entry point for a plain
                // authorizeHttpRequests()-only chain (no formLogin/httpBasic) is
                // Http403ForbiddenEntryPoint, which returns 403 even for a request that
                // presents NO credentials at all. Missing/invalid credentials should be
                // 401; "authenticated but lacks permission" (handled by
                // GlobalExceptionHandler#handleAccessDenied, thrown by @PreAuthorize)
                // stays 403.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Restricted to /v1/public/** by its own shouldNotFilter, so an API key can never
                // authenticate against the user-facing API.
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication is required\",\"path\":\""
                            + request.getRequestURI() + "\"}");
        };
    }

    /**
     * Origins come from {@code forge.cors.allowed-origins} (see CorsProperties). They are set as
     * exact allowed origins rather than patterns: this configuration sets
     * {@code allowCredentials(true)}, and a credentialed response may not echo a wildcard origin -
     * browsers reject it. The previous {@code allowedOriginPatterns("*")} both ignored the
     * configured value and let any site on the internet make credentialed calls to this API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Tenant-Id", "X-Api-Key"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Delegating encoder defaulting to bcrypt (industry-standard, well-supported by Spring
     * Security's tooling); Argon2 is registered and selectable per-hash via the
     * {@code {argon2}} prefix for tenants/deployments that require it, without a migration.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String defaultEncoder = "bcrypt";
        Map<String, PasswordEncoder> encoders = Map.of(
                "bcrypt", new BCryptPasswordEncoder(12),
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(defaultEncoder, encoders);
        delegating.setDefaultPasswordEncoderForMatches(encoders.get(defaultEncoder));
        return delegating;
    }
}

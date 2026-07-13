package com.zentrox.forge.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans com.zentrox.forge.repository for repositories that are NOT tenant-scoped
 * (currently: TenantRepository only - a Tenant row IS the isolation boundary, so it
 * is looked up globally, e.g. by slug during login). Uses the default SimpleJpaRepository
 * base implementation.
 *
 * See {@link TenantScopedRepositoryConfig} for the sibling scan of repository.tenant,
 * which uses a custom base class to make cross-tenant leakage structurally hard.
 * Declaring explicit {@code @EnableJpaRepositories} beans means Spring Boot's own
 * repository auto-configuration backs off, so both scans are required together.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.zentrox.forge.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.zentrox\\.forge\\.repository\\.tenant\\..*"))
public class JpaRepositoryConfig {
}

package com.zentrox.forge.config;

import com.zentrox.forge.repository.tenant.TenantScopedRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scans com.zentrox.forge.repository.tenant with {@link TenantScopedRepositoryImpl} as the
 * base implementation class, so every repository declared there (UserRepository,
 * RoleRepository, WorkflowDefinitionRepository, WorkflowInstanceRepository,
 * AuditLogRepository) gets the tenant-leak guardrails described on that class.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.zentrox.forge.repository.tenant",
        repositoryBaseClass = TenantScopedRepositoryImpl.class)
public class TenantScopedRepositoryConfig {
}

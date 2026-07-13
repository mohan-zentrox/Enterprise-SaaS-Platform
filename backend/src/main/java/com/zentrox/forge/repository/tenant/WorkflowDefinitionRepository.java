package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.WorkflowDefinition;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends TenantScopedRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}

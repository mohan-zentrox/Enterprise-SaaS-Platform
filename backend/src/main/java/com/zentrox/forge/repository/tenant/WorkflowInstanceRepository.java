package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.WorkflowInstance;

import java.util.List;
import java.util.UUID;

public interface WorkflowInstanceRepository extends TenantScopedRepository<WorkflowInstance, UUID> {

    List<WorkflowInstance> findAllByTenantIdAndWorkflowDefinitionId(UUID tenantId, UUID workflowDefinitionId);
}

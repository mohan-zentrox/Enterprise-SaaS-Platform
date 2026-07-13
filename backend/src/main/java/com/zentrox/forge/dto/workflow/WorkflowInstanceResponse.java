package com.zentrox.forge.dto.workflow;

import com.zentrox.forge.entity.WorkflowInstance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowInstanceResponse(
        UUID id,
        UUID workflowDefinitionId,
        String currentState,
        List<WorkflowHistoryEntry> history,
        Instant createdAt,
        Instant updatedAt
) {

    public static WorkflowInstanceResponse from(WorkflowInstance entity, List<WorkflowHistoryEntry> history) {
        return new WorkflowInstanceResponse(entity.getId(), entity.getWorkflowDefinitionId(),
                entity.getCurrentState(), history, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

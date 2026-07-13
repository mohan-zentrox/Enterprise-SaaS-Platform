package com.zentrox.forge.dto.workflow;

import com.zentrox.forge.entity.WorkflowDefinition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowDefinitionResponse(
        UUID id,
        String name,
        String description,
        List<String> states,
        List<WorkflowTransitionRule> transitions,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public static WorkflowDefinitionResponse from(WorkflowDefinition entity, WorkflowDefinitionPayload payload) {
        return new WorkflowDefinitionResponse(entity.getId(), entity.getName(), entity.getDescription(),
                payload.states(), payload.transitions(), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

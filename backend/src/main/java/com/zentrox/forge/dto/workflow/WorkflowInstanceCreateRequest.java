package com.zentrox.forge.dto.workflow;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WorkflowInstanceCreateRequest(@NotNull UUID workflowDefinitionId) {
}

package com.zentrox.forge.dto.workflow;

import jakarta.validation.constraints.NotBlank;

public record WorkflowInstanceTransitionRequest(@NotBlank String toState) {
}

package com.zentrox.forge.dto.workflow;

import jakarta.validation.constraints.NotBlank;

/** One allowed state transition within a WorkflowDefinition. */
public record WorkflowTransitionRule(@NotBlank String from, @NotBlank String to) {
}

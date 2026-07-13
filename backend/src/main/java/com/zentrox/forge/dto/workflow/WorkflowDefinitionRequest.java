package com.zentrox.forge.dto.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkflowDefinitionRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description,

        @NotEmpty
        List<@NotBlank String> states,

        @Valid
        List<WorkflowTransitionRule> transitions
) {
}

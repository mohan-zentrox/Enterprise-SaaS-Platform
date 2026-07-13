package com.zentrox.forge.dto.workflow;

import java.util.List;

/**
 * The shape persisted in {@code workflow_definitions.definition_json}. Kept separate
 * from the request/response DTOs so the on-disk JSON schema can evolve independently
 * of the HTTP contract if needed.
 */
public record WorkflowDefinitionPayload(List<String> states, List<WorkflowTransitionRule> transitions) {
}

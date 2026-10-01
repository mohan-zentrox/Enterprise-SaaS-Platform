package com.zentrox.forge.notification.event;

import java.util.UUID;

/** Published by WorkflowService after a transition is applied and committed. */
public record WorkflowTransitionedEvent(UUID tenantId, UUID instanceId, String workflowName, String fromState,
                                         String toState, UUID actorUserId) {
}

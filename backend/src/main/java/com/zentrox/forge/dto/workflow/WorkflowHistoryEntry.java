package com.zentrox.forge.dto.workflow;

import java.time.Instant;
import java.util.UUID;

/** One entry in {@code workflow_instances.history_json}, appended on every transition. */
public record WorkflowHistoryEntry(String from, String to, Instant at, UUID by) {
}

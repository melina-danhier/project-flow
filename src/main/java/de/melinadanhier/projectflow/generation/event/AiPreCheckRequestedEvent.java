package de.melinadanhier.projectflow.generation.event;

import java.util.UUID;

public record AiPreCheckRequestedEvent(UUID workflowId, UUID runId) {
    public AiPreCheckRequestedEvent(UUID workflowId) { this(workflowId, null); }
}

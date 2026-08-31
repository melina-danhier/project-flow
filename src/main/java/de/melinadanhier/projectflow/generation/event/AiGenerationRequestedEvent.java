package de.melinadanhier.projectflow.generation.event;

import java.util.UUID;

public record AiGenerationRequestedEvent(UUID workflowId, UUID runId) {
    public AiGenerationRequestedEvent(UUID workflowId) { this(workflowId, null); }
}

package de.melinadanhier.projectflow.generation.event;

import java.util.Objects;
import java.util.UUID;

public record AiGenerationRequestedEvent(UUID workflowId, UUID runId) {
    public AiGenerationRequestedEvent {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
    }
}

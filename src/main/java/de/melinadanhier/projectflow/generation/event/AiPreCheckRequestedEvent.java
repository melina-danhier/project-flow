package de.melinadanhier.projectflow.generation.event;

import java.util.Objects;
import java.util.UUID;

public record AiPreCheckRequestedEvent(UUID workflowId, UUID runId) {
    public AiPreCheckRequestedEvent {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
    }
}

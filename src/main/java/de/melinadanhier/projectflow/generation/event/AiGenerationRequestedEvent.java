package de.melinadanhier.projectflow.generation.event;

import java.util.UUID;

public record AiGenerationRequestedEvent(UUID workflowId) { }

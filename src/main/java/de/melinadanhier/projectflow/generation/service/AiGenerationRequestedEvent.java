package de.melinadanhier.projectflow.generation.service;

import java.util.UUID;

public record AiGenerationRequestedEvent(UUID workflowId) { }

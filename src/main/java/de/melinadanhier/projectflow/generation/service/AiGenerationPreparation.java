package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;

import java.util.List;
import java.util.UUID;

public record AiGenerationPreparation(
        UUID workflowId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> ignoredWarnings
) { }

package de.melinadanhier.projectflow.generation.model;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;

import java.util.List;
import java.util.UUID;

public record AiGenerationPreparation(
        UUID workflowId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> ignoredWarnings
) { }

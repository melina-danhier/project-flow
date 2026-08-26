package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;

import java.util.List;
import java.util.UUID;

public record AiGenerationWork(
        UUID workflowId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> acknowledgedWarnings,
        int roundAttemptCount,
        String promptVersion
) { }

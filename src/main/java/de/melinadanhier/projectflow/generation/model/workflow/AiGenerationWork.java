package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiGenerationWork(
        UUID workflowId,
        UUID runId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> acceptedOpenPoints,
        int roundAttemptCount
) {
    public AiGenerationWork {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        acceptedOpenPoints = acceptedOpenPoints == null ? List.of() : List.copyOf(acceptedOpenPoints);
    }
}

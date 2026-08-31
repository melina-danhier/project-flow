package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;

import java.util.List;
import java.util.UUID;
import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;

public record AiGenerationWork(
        UUID workflowId,
        UUID runId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> acknowledgedWarnings,
        List<String> confirmedAssumptions,
        List<RejectedCriticalAssumption> rejectedAssumptions,
        int roundAttemptCount,
        String promptVersion
) {
    public AiGenerationWork(UUID workflowId, UUID runId, AiWizardSnapshot snapshot,
                            List<AiPreCheckProblem> acknowledgedWarnings,
                            int roundAttemptCount, String promptVersion) {
        this(workflowId, runId, snapshot, acknowledgedWarnings, List.of(), List.of(),
                roundAttemptCount, promptVersion);
    }

    public AiGenerationWork(UUID workflowId, AiWizardSnapshot snapshot,
                            List<AiPreCheckProblem> acknowledgedWarnings,
                            int roundAttemptCount, String promptVersion) {
        this(workflowId, null, snapshot, acknowledgedWarnings, List.of(), List.of(),
                roundAttemptCount, promptVersion);
    }
}

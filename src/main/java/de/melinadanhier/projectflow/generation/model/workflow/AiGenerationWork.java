package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiGenerationWork(
        UUID workflowId,
        UUID runId,
        AiWizardSnapshot snapshot,
        List<AiPreCheckProblem> acknowledgedWarnings,
        List<String> confirmedAssumptions,
        List<RejectedCriticalAssumption> rejectedAssumptions,
        int roundAttemptCount
) {
    public AiGenerationWork {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(runId, "runId");
        acknowledgedWarnings = acknowledgedWarnings == null ? List.of() : List.copyOf(acknowledgedWarnings);
        confirmedAssumptions = confirmedAssumptions == null ? List.of() : List.copyOf(confirmedAssumptions);
        rejectedAssumptions = rejectedAssumptions == null ? List.of() : List.copyOf(rejectedAssumptions);
    }

    public AiGenerationWork(UUID workflowId, UUID runId, AiWizardSnapshot snapshot,
                            List<AiPreCheckProblem> acknowledgedWarnings,
                            int roundAttemptCount) {
        this(workflowId, runId, snapshot, acknowledgedWarnings, List.of(), List.of(),
                roundAttemptCount);
    }
}

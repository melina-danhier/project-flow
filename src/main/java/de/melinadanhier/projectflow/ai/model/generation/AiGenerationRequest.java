package de.melinadanhier.projectflow.ai.model.generation;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.util.List;
import java.util.Objects;

public record AiGenerationRequest(
        AiWizardSnapshot confirmedWizardData,
        List<AiPreCheckProblem> acknowledgedWarnings,
        List<String> previousValidationIssues,
        List<String> confirmedAssumptions,
        List<RejectedCriticalAssumption> rejectedAssumptions
) {
    public AiGenerationRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        acknowledgedWarnings = acknowledgedWarnings == null
                ? List.of()
                : List.copyOf(acknowledgedWarnings);
        if (acknowledgedWarnings.stream()
                .anyMatch(problem -> problem.severity() != AiPreCheckSeverity.WARNING)) {
            throw new IllegalArgumentException("acknowledgedWarnings darf nur Warnungen enthalten");
        }
        previousValidationIssues = previousValidationIssues == null
                ? List.of()
                : List.copyOf(previousValidationIssues);
        confirmedAssumptions = confirmedAssumptions == null ? List.of() : List.copyOf(confirmedAssumptions);
        rejectedAssumptions = rejectedAssumptions == null ? List.of() : List.copyOf(rejectedAssumptions);
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> acknowledgedWarnings
    ) {
        this(confirmedWizardData, acknowledgedWarnings, List.of(),
                List.of(), List.of());
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> acknowledgedWarnings,
            List<String> previousValidationIssues
    ) {
        this(confirmedWizardData, acknowledgedWarnings, previousValidationIssues,
                List.of(), List.of());
    }
}

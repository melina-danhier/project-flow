package de.melinadanhier.projectflow.ai.model.generation;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.util.List;
import java.util.Objects;

public record AiGenerationRequest(
        AiWizardSnapshot confirmedWizardData,
        List<AiPreCheckProblem> acceptedOpenPoints,
        List<String> previousValidationIssues
) {
    public AiGenerationRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        acceptedOpenPoints = acceptedOpenPoints == null
                ? List.of()
                : List.copyOf(acceptedOpenPoints);
        if (acceptedOpenPoints.stream()
                .anyMatch(problem -> problem.severity() != AiPreCheckSeverity.WARNING)) {
            throw new IllegalArgumentException("acceptedOpenPoints darf nur nicht blockierende Punkte enthalten");
        }
        previousValidationIssues = previousValidationIssues == null
                ? List.of()
                : List.copyOf(previousValidationIssues);
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> acceptedOpenPoints
    ) {
        this(confirmedWizardData, acceptedOpenPoints, List.of());
    }
}

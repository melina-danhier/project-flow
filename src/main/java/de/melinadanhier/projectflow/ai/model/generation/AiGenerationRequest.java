package de.melinadanhier.projectflow.ai.model.generation;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;

public record AiGenerationRequest(
        @NotNull @Valid AiWizardSnapshot confirmedWizardData,
        List<@Valid AiPreCheckProblem> acknowledgedWarnings,
        List<String> previousValidationIssues,
        @NotNull String promptVersion
) {
    public AiGenerationRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        Objects.requireNonNull(promptVersion,"promptVersion darf nicht null sein");
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
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> acknowledgedWarnings
    ) {
        this(confirmedWizardData, acknowledgedWarnings, List.of(), AiPromptVersions.GENERATION_PROMPT);
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> acknowledgedWarnings,
            List<String> previousValidationIssues
    ) {
        this(confirmedWizardData, acknowledgedWarnings, previousValidationIssues, AiPromptVersions.GENERATION_PROMPT);
    }
}

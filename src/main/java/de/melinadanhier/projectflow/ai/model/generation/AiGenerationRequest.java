package de.melinadanhier.projectflow.ai.model.generation;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

public record AiGenerationRequest(
        @NotNull @Valid AiWizardSnapshot confirmedWizardData,
        List<@Valid AiPreCheckProblem> explicitlyIgnoredWarnings,
        List<String> previousValidationIssues
) {
    public AiGenerationRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        explicitlyIgnoredWarnings = explicitlyIgnoredWarnings == null ? List.of()
                : List.copyOf(explicitlyIgnoredWarnings);
        previousValidationIssues = previousValidationIssues == null ? List.of()
                : List.copyOf(previousValidationIssues);
    }

    public AiGenerationRequest(
            AiWizardSnapshot confirmedWizardData,
            List<AiPreCheckProblem> explicitlyIgnoredWarnings
    ) {
        this(confirmedWizardData, explicitlyIgnoredWarnings, List.of());
    }
}

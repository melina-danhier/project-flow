package de.melinadanhier.projectflow.ai.model.precheck;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.List;

public record AiPreCheckRequest(
        @NotNull AiWizardSnapshot confirmedWizardData,
        List<String> previousValidationIssues
) {
    public AiPreCheckRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        previousValidationIssues = previousValidationIssues == null ? List.of()
                : List.copyOf(previousValidationIssues);
    }

    public AiPreCheckRequest(AiWizardSnapshot confirmedWizardData) {
        this(confirmedWizardData, List.of());
    }
}

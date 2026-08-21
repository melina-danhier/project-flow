package de.melinadanhier.projectflow.generation.dto.request;

import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

public record AiGenerationRequest(
        @NotNull AiWizardSnapshot confirmedWizardData,
        @NotNull List<@Valid AiPreCheckProblem> explicitlyIgnoredWarnings
) {
    public AiGenerationRequest {
        Objects.requireNonNull(confirmedWizardData, "confirmedWizardData darf nicht null sein");
        explicitlyIgnoredWarnings = explicitlyIgnoredWarnings == null
                ? List.of()
                : List.copyOf(explicitlyIgnoredWarnings);
    }
}

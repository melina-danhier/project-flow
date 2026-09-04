package de.melinadanhier.projectflow.ai.model.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPreCheckProblem(
        @NotNull AiPreCheckSeverity severity,
        @NotBlank @Size(max = 1000) String message,
        @NotBlank @Size(max = 1000) String suggestion
) {
    public AiPreCheckProblem {
        message = message == null ? null : message.trim();
        suggestion = suggestion == null ? null : suggestion.trim();
    }
}

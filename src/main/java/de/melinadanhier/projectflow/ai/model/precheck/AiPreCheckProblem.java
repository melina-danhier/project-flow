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
        message = trim(message);
        suggestion = trim(suggestion);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

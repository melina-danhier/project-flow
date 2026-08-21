package de.melinadanhier.projectflow.generation.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPreCheckProblem(
        @NotNull AiPreCheckSeverity severity,
        @NotBlank @Size(max = 1000) String message,
        @NotBlank @Size(max = 1000) String suggestion
) {
}

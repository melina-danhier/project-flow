package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GeneratedPlanMetadata(
        @NotBlank @Size(max = 1000) String summary,
        @NotNull List<@Size(max = 1000) String> assumptions
) {
    public GeneratedPlanMetadata {
        assumptions = assumptions == null ? null : List.copyOf(assumptions);
    }
}

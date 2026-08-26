package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_ASSUMPTIONS;

public record GeneratedPlanMetadata(
        @NotBlank @Size(max = 1000) String summary,
        @NotNull @Size(max = MAX_ASSUMPTIONS) List<@Size(max = 1000) String> assumptions
) {
    public GeneratedPlanMetadata {
        summary = trim(summary);
        assumptions = assumptions == null ? null : assumptions.stream()
                .map(GeneratedPlanMetadata::trim)
                .toList();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

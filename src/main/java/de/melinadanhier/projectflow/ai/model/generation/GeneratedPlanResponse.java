package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GeneratedPlanResponse(
        @NotNull @Valid GeneratedPlanMetadata metadata,
        @NotNull List<@Valid GeneratedPhase> phases
) {
    public GeneratedPlanResponse {
        phases = phases == null ? null : List.copyOf(phases);
    }

}

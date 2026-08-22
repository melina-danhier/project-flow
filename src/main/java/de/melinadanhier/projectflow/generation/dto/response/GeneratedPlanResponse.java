package de.melinadanhier.projectflow.generation.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

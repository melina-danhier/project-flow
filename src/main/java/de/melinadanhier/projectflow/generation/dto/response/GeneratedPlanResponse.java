package de.melinadanhier.projectflow.generation.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GeneratedPlanResponse(
        @NotNull String schemaVersion,
        @NotNull @Valid GeneratedPlanMetadata metadata,
        @NotEmpty List<@Valid GeneratedPhase> phases
) {
    public GeneratedPlanResponse {
        phases = phases == null ? null : List.copyOf(phases);
    }
}

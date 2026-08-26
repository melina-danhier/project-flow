package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_PHASES;
import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MIN_PHASES;

public record GeneratedPlanResponse(
        @NotNull @Valid GeneratedPlanMetadata metadata,
        @NotNull @Size(min = MIN_PHASES, max = MAX_PHASES) List<@Valid GeneratedPhase> phases
) {
    public GeneratedPlanResponse {
        phases = phases == null ? null : List.copyOf(phases);
    }

}

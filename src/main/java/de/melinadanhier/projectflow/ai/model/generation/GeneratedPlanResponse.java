package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_SECTIONS;
import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MIN_SECTIONS;

public record GeneratedPlanResponse(
        @NotNull @Size(min = MIN_SECTIONS, max = MAX_SECTIONS) List<@Valid GeneratedSection> sections
) {
    public GeneratedPlanResponse {
        sections = sections == null ? null : List.copyOf(sections);
    }

}

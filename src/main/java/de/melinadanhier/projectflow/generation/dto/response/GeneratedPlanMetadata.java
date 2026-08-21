package de.melinadanhier.projectflow.generation.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Metadaten zur Einordnung des Entwurfs, ohne bestätigte allgemeine Projektdaten zu duplizieren. */
public record GeneratedPlanMetadata(
        @NotBlank @Size(max = 1000) String summary,
        @NotNull List<@Size(max = 1000) String> assumptions
) {
    public GeneratedPlanMetadata {
        assumptions = assumptions == null ? null : List.copyOf(assumptions);
    }
}

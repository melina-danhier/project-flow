package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GeneratedMilestone(
        @NotBlank @Size(max = 100) String tempId,
        @NotBlank @Size(max = 100) String title,
        LocalDate date,
        @Positive int order
) {
}

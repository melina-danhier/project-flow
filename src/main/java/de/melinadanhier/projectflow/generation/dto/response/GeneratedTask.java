package de.melinadanhier.projectflow.generation.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GeneratedTask(
        @NotBlank @Size(max = 100) String tempId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        @Positive Integer estimatedHours,
        LocalDate startDate,
        LocalDate dueDate,
        @Size(max = 2000) String criticalAssumption,
        @NotNull GeneratedElementOrigin origin,
        @Positive int order
) {
}

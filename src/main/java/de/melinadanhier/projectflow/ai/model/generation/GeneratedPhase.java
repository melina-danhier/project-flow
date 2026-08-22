package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.LocalDate;

public record GeneratedPhase(
        @NotBlank @Size(max = 100) String tempId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        LocalDate startDate,
        LocalDate endDate,
        @Positive int order,
        @NotNull List<@Valid GeneratedTask> tasks,
        @NotNull List<@Valid GeneratedMilestone> milestones
) {
    public GeneratedPhase {
        tasks = tasks == null ? null : List.copyOf(tasks);
        milestones = milestones == null ? null : List.copyOf(milestones);
    }
}

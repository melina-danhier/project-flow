package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;

public record GeneratedSection(
        @Size(max = 100) String tempId,
        @NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
        @Size(max = MAX_DESCRIPTION_LENGTH) String description,
        @Positive int order,
        @NotNull @Size(max = MAX_TASKS) List<@Valid GeneratedTask> tasks,
        @NotNull @Size(max = MAX_MILESTONES) List<@Valid GeneratedMilestone> milestones
) {
    public GeneratedSection {
        tempId = trim(tempId);
        title = trim(title);
        description = trim(description);
        tasks = tasks == null ? null : List.copyOf(tasks);
        milestones = milestones == null ? null : List.copyOf(milestones);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

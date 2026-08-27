package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;

public record GeneratedTask(
        @NotBlank @Size(max = 100) String tempId,
        @NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
        @Size(max = MAX_DESCRIPTION_LENGTH) String description,
        @Positive @Max(MAX_ESTIMATED_HOURS) Integer estimatedHours,
        LocalDate startDate,
        LocalDate dueDate,
        @Size(max = MAX_DESCRIPTION_LENGTH) String criticalAssumption,
        @NotNull GeneratedElementOrigin origin,
        @Positive int order,
        @NotNull List<@NotBlank @Size(max = 100) String> prerequisiteTaskTempIds,
        TaskPriority priority
) {
    public GeneratedTask {
        tempId = trim(tempId);
        title = trim(title);
        description = trim(description);
        criticalAssumption = criticalAssumption == null || criticalAssumption.isBlank()
                ? null : criticalAssumption.strip();
        prerequisiteTaskTempIds = prerequisiteTaskTempIds == null ? null
                : prerequisiteTaskTempIds.stream().map(GeneratedTask::trim).toList();
    }

    public GeneratedTask(String tempId, String title, String description, Integer estimatedHours,
                         LocalDate startDate, LocalDate dueDate, String criticalAssumption,
                         GeneratedElementOrigin origin, int order) {
        this(tempId, title, description, estimatedHours, startDate, dueDate, criticalAssumption,
                origin, order, List.of(), null);
    }

    public GeneratedTask(String tempId, String title, String description, Integer estimatedHours,
                         LocalDate startDate, LocalDate dueDate, String criticalAssumption,
                         GeneratedElementOrigin origin, int order,
                         List<String> prerequisiteTaskTempIds) {
        this(tempId, title, description, estimatedHours, startDate, dueDate, criticalAssumption,
                origin, order, prerequisiteTaskTempIds, null);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

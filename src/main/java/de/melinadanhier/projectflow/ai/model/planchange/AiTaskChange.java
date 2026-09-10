package de.melinadanhier.projectflow.ai.model.planchange;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import java.time.LocalDate;
import java.util.List;

public record AiTaskChange(
        AiPlanChangeOperation operation, String existingTaskId, String targetSectionId,
        List<String> changedFields, String title, String description, TaskPriority priority,
        Integer estimatedHours, LocalDate startDate, LocalDate dueDate,
        AiRelativePlacement placement, String explanation) { }

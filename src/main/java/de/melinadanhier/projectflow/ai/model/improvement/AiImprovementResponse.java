package de.melinadanhier.projectflow.ai.model.improvement;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import java.time.LocalDate;

public record AiImprovementResponse(
        AiImprovementElementType elementType,
        String title,
        String description,
        TaskPriority priority,
        Integer estimatedHours,
        LocalDate startDate,
        LocalDate dueDate,
        AiReplanPlacementResponse placement,
        String explanation
) {
    public AiImprovementResponse(
            AiImprovementElementType elementType,
            String title,
            String description,
            TaskPriority priority,
            Integer estimatedHours,
            LocalDate startDate,
            LocalDate dueDate,
            String explanation
    ) {
        this(elementType, title, description, priority, estimatedHours, startDate, dueDate, null, explanation);
    }

    public AiImprovementResponse(
            AiImprovementElementType elementType,
            String title,
            String description,
            TaskPriority priority,
            Integer estimatedHours,
            LocalDate startDate,
            LocalDate dueDate
    ) {
        this(elementType, title, description, priority, estimatedHours, startDate, dueDate, null, null);
    }

    public AiImprovementContent toContent() {
        return new AiImprovementContent(
                elementType, title, description, priority, estimatedHours, startDate, dueDate);
    }
}

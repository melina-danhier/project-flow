package de.melinadanhier.projectflow.ai.model.improvement;

import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import java.time.LocalDate;
import java.io.Serializable;

/** Ausschließlich die fachlich für eine einzelne KI-Verbesserung freigegebenen Felder. */
public record AiImprovementContent(
        AiImprovementElementType elementType,
        String title,
        String description,
        TaskPriority priority,
        Integer estimatedHours,
        LocalDate startDate,
        LocalDate dueDate
) implements Serializable { }

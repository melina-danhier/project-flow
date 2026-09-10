package de.melinadanhier.projectflow.ai.model.improvement;

import java.time.LocalDate;

/** Ausschließlich die für REPLAN freigegebenen Task-Felder. */
public record AiTaskReplanResponse(
        LocalDate startDate,
        LocalDate dueDate,
        AiReplanPlacementResponse placement,
        String explanation
) { }

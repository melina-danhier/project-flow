package de.melinadanhier.projectflow.ai.model.improvement;

import java.time.LocalDate;

/** Ausschließlich die für REPLAN freigegebenen Milestone-Felder. */
public record AiMilestoneReplanResponse(
        LocalDate dueDate,
        AiReplanPlacementResponse placement,
        String explanation
) { }

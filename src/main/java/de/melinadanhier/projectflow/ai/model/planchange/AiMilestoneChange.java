package de.melinadanhier.projectflow.ai.model.planchange;

import java.time.LocalDate;
import java.util.List;

public record AiMilestoneChange(
        AiPlanChangeOperation operation, String existingMilestoneId, String targetSectionId,
        List<String> changedFields, String title, String description, LocalDate dueDate,
        AiRelativePlacement placement, String explanation) { }

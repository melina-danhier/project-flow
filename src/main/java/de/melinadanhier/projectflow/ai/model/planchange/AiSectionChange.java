package de.melinadanhier.projectflow.ai.model.planchange;

import java.util.List;

public record AiSectionChange(
        AiPlanChangeOperation operation, String existingSectionId, String newSectionReference, List<String> changedFields,
        String title, String description, String beforeSectionId, String afterSectionId,
        String explanation) { }

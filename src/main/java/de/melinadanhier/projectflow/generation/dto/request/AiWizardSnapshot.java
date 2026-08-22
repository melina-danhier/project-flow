package de.melinadanhier.projectflow.generation.dto.request;

import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;

import java.time.LocalDate;

public record AiWizardSnapshot(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        CollaborationMode collaborationMode,
        TemplateCategory category,
        String projectType,
        String projectGoal,
        String constraints,
        String additionalInformation,
        AiProjectTimeFrameType timeFrameType,
        Integer durationDays
) {
    public AiWizardSnapshot(
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            CollaborationMode collaborationMode,
            TemplateCategory category,
            String projectType,
            String projectGoal,
            String constraints,
            String additionalInformation
    ) {
        this(title, description, startDate, endDate, collaborationMode, category, projectType,
                projectGoal, constraints, additionalInformation, null, null);
    }
}

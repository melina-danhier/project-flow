package de.melinadanhier.projectflow.generation.model.wizard;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
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
        ProjectSubCategory subcategory,
        String otherProjectTypeDescription,
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
            ProjectSubCategory subcategory,
            String otherProjectTypeDescription,
            String projectGoal,
            String constraints,
            String additionalInformation
    ) {
        this(title, description, startDate, endDate, collaborationMode, category, subcategory, otherProjectTypeDescription,
                projectGoal, constraints, additionalInformation, null, null);
    }
}

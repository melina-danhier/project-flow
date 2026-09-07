package de.melinadanhier.projectflow.generation.model.wizard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import java.time.LocalDate;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        Integer durationDays,
        String availableWorkingTime,
        Map<String, String> projectSpecificAnswers
) {
    public AiWizardSnapshot {
        projectSpecificAnswers = projectSpecificAnswers == null ? Map.of() : Map.copyOf(projectSpecificAnswers);
        validateTimeFrame(startDate, endDate, durationDays);
        if (availableWorkingTime != null && availableWorkingTime.length() > 1000) {
            throw new IllegalArgumentException("Die verfügbare Arbeitszeit darf höchstens 1000 Zeichen lang sein.");
        }
        if (additionalInformation != null && additionalInformation.length() > 2000) {
            throw new IllegalArgumentException("Die weiteren Hinweise dürfen höchstens 2000 Zeichen lang sein.");
        }
    }

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
                projectGoal, constraints, additionalInformation, null, null,
                Map.of());
    }

    public AiWizardSnapshot(
            String title, String description, LocalDate startDate, LocalDate endDate,
            CollaborationMode collaborationMode, TemplateCategory category, ProjectSubCategory subcategory,
            String otherProjectTypeDescription, String projectGoal, String constraints,
            String additionalInformation, Integer durationDays, String availableWorkingTime
    ) {
        this(title, description, startDate, endDate, collaborationMode, category, subcategory,
                otherProjectTypeDescription, projectGoal, constraints, additionalInformation,
                durationDays, availableWorkingTime, Map.of());
    }

    private static void validateTimeFrame(LocalDate startDate, LocalDate endDate, Integer durationDays) {
        if (durationDays != null && durationDays < 1) {
            throw new IllegalArgumentException("Die Projektdauer muss mindestens einen Tag betragen.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Das Projektende darf nicht vor dem Projektstart liegen.");
        }
        if (startDate != null && endDate != null && durationDays != null
                && java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1 != durationDays) {
            throw new IllegalArgumentException(
                    "Projektstart, Projektende und Projektdauer widersprechen sich.");
        }
    }
}

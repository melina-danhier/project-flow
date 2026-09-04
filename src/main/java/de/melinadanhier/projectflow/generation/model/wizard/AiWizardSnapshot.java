package de.melinadanhier.projectflow.generation.model.wizard;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;

import java.time.LocalDate;
import java.util.Map;

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
        Integer durationDays,
        Map<String, String> projectSpecificAnswers
) {
    public AiWizardSnapshot {
        projectSpecificAnswers = projectSpecificAnswers == null ? Map.of() : Map.copyOf(projectSpecificAnswers);
        validateTimeFrame(startDate, endDate, timeFrameType, durationDays);
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
            String additionalInformation, AiProjectTimeFrameType timeFrameType, Integer durationDays
    ) {
        this(title, description, startDate, endDate, collaborationMode, category, subcategory,
                otherProjectTypeDescription, projectGoal, constraints, additionalInformation,
                timeFrameType, durationDays, Map.of());
    }

    private static void validateTimeFrame(LocalDate startDate, LocalDate endDate,
                                          AiProjectTimeFrameType timeFrameType, Integer durationDays) {
        // Persisted legacy snapshots may not contain type/duration metadata.
        if (timeFrameType == null) {
            if (durationDays != null) {
                throw new IllegalArgumentException("Eine Dauer benötigt eine Zeitrahmen-Art.");
            }
            return;
        }
        if (durationDays != null && durationDays < 1) {
            throw new IllegalArgumentException("Die Projektdauer muss mindestens einen Tag betragen.");
        }
        switch (timeFrameType) {
            case NONE -> {
                if (startDate != null || endDate != null || durationDays != null) {
                    throw new IllegalArgumentException("Ohne Zeitrahmen dürfen keine Datums- oder Dauerwerte vorliegen.");
                }
            }
            case START_AND_END -> {
                requireDates(startDate, endDate);
                if (durationDays != null) {
                    throw new IllegalArgumentException("Ein fester Projektzeitraum darf keine zusätzliche Dauer enthalten.");
                }
            }
            case START_AND_DURATION -> {
                requireDatesAndDuration(startDate, endDate, durationDays);
                if (!endDate.equals(startDate.plusDays(durationDays - 1L))) {
                    throw new IllegalArgumentException("Projektstart, Projektdauer und berechnetes Enddatum widersprechen sich.");
                }
            }
            case END_AND_DURATION -> {
                requireDatesAndDuration(startDate, endDate, durationDays);
                if (!startDate.equals(endDate.minusDays(durationDays - 1L))) {
                    throw new IllegalArgumentException("Projektende, Projektdauer und berechnetes Startdatum widersprechen sich.");
                }
            }
        }
    }

    private static void requireDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Der Projektzeitraum benötigt einen gültigen Start und ein gültiges Ende.");
        }
    }

    private static void requireDatesAndDuration(LocalDate startDate, LocalDate endDate, Integer durationDays) {
        requireDates(startDate, endDate);
        if (durationDays == null) {
            throw new IllegalArgumentException("Für diese Zeitrahmen-Art ist eine Projektdauer erforderlich.");
        }
    }
}

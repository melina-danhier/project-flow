package de.melinadanhier.projectflow.wizard.dto;

import java.time.LocalDate;
import java.util.List;

public record AiWizardSummary(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean groupProject,
        String category,
        String creationType,
        Integer durationDays,
        Integer durationValue,
        String durationUnit,
        String availableWorkingTime,
        String projectGoal,
        String constraints,
        String additionalInformation,
        List<Answer> projectSpecificAnswers
) {
    public record Answer(String key, String label, String value) { }

    public AiWizardSummary(
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            boolean groupProject,
            String category,
            String creationType,
            Integer durationDays,
            String availableWorkingTime,
            String projectGoal,
            String constraints,
            String additionalInformation,
            List<Answer> projectSpecificAnswers
    ) {
        this(title, description, startDate, endDate, groupProject, category, creationType,
                durationDays, null, null,
                availableWorkingTime, projectGoal, constraints, additionalInformation, projectSpecificAnswers);
    }

    public String formattedDuration() {
        if (durationValue != null && durationUnit != null) {
            return switch (durationUnit.toUpperCase()) {
                case "WEEKS" -> durationValue == 1 ? "1 Woche" : durationValue + " Wochen";
                case "MONTHS" -> durationValue == 1 ? "1 Monat" : durationValue + " Monate";
                default -> durationValue == 1 ? "1 Tag" : durationValue + " Tage";
            };
        }
        if (durationDays != null) {
            return durationDays == 1 ? "1 Tag" : durationDays + " Tage";
        }
        return null;
    }
}

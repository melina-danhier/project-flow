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
        String availableWorkingTime,
        String projectGoal,
        String constraints,
        String additionalInformation,
        List<Answer> projectSpecificAnswers
) {
    public record Answer(String key, String label, String value) { }
}

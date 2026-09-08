package de.melinadanhier.projectflow.plancontainer.template.service;

import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.Task;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class TemplateDatePolicy {

    private TemplateDatePolicy() {
    }

    public static TemplateDateAssessment assess(
            Template template,
            LocalDate projectStartDate,
            LocalDate projectEndDate
    ) {
        int highestRelativeDay = template.getElements().stream()
                .mapToInt(element -> {
                    if (element instanceof Task task) {
                        return Math.max(valueOrMinusOne(task.getRelativeStartDay()),
                                valueOrMinusOne(task.getRelativeDueDay()));
                    }
                    if (element instanceof Milestone milestone) {
                        return valueOrMinusOne(milestone.getRelativeDueDay());
                    }
                    return -1;
                })
                .max()
                .orElse(-1);
        boolean hasRelativeDates = highestRelativeDay >= 0;
        int recommendedDuration = template.getRecommendedDurationDays() == null
                ? 0 : template.getRecommendedDurationDays();
        int templateDurationDays = Math.max(recommendedDuration, highestRelativeDay + 1);
        boolean convertible = hasRelativeDates && (projectStartDate != null || projectEndDate != null);
        LocalDate conversionStartDate = projectStartDate;
        if (conversionStartDate == null && projectEndDate != null && templateDurationDays > 0) {
            conversionStartDate = projectEndDate.minusDays(templateDurationDays - 1L);
        }
        Long projectDurationDays = projectStartDate != null && projectEndDate != null
                ? ChronoUnit.DAYS.between(projectStartDate, projectEndDate) + 1L
                : null;
        boolean boundaryConflict = hasRelativeDates && projectDurationDays != null
                && templateDurationDays > projectDurationDays;
        return new TemplateDateAssessment(
                hasRelativeDates,
                convertible,
                boundaryConflict,
                conversionStartDate,
                templateDurationDays,
                projectDurationDays
        );
    }

    private static int valueOrMinusOne(Integer value) {
        return value == null ? -1 : value;
    }
}

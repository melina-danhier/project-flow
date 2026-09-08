package de.melinadanhier.projectflow.plancontainer.template.service;

import java.time.LocalDate;

public record TemplateDateAssessment(
        boolean hasRelativeDates,
        boolean convertible,
        boolean boundaryConflict,
        LocalDate conversionStartDate,
        int templateDurationDays,
        Long projectDurationDays
) {

    public boolean requiresConfirmation() {
        return hasRelativeDates && (!convertible || boundaryConflict);
    }
}

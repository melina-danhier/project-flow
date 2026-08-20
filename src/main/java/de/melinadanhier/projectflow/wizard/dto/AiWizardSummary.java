package de.melinadanhier.projectflow.wizard.dto;

import java.time.LocalDate;

public record AiWizardSummary(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean groupProject,
        String category,
        String projectGoal,
        String constraints,
        String additionalInformation
) { }

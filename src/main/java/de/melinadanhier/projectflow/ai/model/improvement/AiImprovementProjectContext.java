package de.melinadanhier.projectflow.ai.model.improvement;

import java.time.LocalDate;

public record AiImprovementProjectContext(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate
) { }

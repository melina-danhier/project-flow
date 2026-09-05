package de.melinadanhier.projectflow.generation.dto.assumption;

public record CriticalAssumptionReviewDto(
        int index,
        String statement,
        boolean correctionRequiredIfRejected,
        AssumptionDecision decision,
        String correction
) { }

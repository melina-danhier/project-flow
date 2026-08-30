package de.melinadanhier.projectflow.generation.dto;

public record CriticalAssumptionReviewDto(
        int index,
        String statement,
        boolean correctionRequiredIfRejected,
        AssumptionDecision decision,
        String correction
) { }

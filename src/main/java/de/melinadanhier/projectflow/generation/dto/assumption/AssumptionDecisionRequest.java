package de.melinadanhier.projectflow.generation.dto.assumption;

public record AssumptionDecisionRequest(
        int assumptionIndex,
        AssumptionDecision decision,
        String correction
) { }

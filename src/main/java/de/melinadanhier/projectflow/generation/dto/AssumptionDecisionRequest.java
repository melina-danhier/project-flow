package de.melinadanhier.projectflow.generation.dto;

public record AssumptionDecisionRequest(
        int assumptionIndex,
        AssumptionDecision decision,
        String correction
) { }

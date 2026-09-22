package de.melinadanhier.projectflow.ai.model.improvement;

/** Ausschließlich das für ESTIMATE_EFFORT freigegebene Task-Feld. */
public record AiTaskEffortResponse(Integer estimatedMinutes, String explanation) { }

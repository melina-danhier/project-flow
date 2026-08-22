package de.melinadanhier.projectflow.generation.dto;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;

public record AiPreCheckProblemDto(
        int index,
        AiPreCheckSeverity severity,
        String message,
        String suggestion
) {
    public boolean isWarning() {
        return severity == AiPreCheckSeverity.WARNING;
    }
}

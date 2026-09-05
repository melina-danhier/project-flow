package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;

public record AiPreCheckProblemDto(
        int index,
        AiPreCheckSeverity severity,
        String message,
        String suggestion,
        boolean acknowledged
) {
    public boolean isWarning() {
        return severity == AiPreCheckSeverity.WARNING;
    }
}

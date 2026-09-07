package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;

public record AiPreCheckProblemDto(
        int index,
        AiPreCheckSeverity severity,
        AiPreCheckProblemType type,
        String message,
        String suggestedUserAction,
        String reviewQuestion,
        String acceptedInterpretation,
        boolean accepted
) {
    public boolean isOpenPoint() {
        return severity == AiPreCheckSeverity.WARNING;
    }
}

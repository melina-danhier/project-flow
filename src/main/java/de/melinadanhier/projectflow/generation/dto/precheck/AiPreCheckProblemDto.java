package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckAdjustmentOption;

import java.util.List;

public record AiPreCheckProblemDto(
        int index,
        AiPreCheckSeverity severity,
        AiPreCheckProblemType type,
        String message,
        String suggestedUserAction,
        String acceptedInterpretation,
        boolean accepted,
        List<AiPreCheckInputChange> proposedInputChanges,
        boolean proposedChangeApplicable,
        List<AiPreCheckAdjustmentOption> adjustmentOptions
) {
    public boolean isOpenPoint() {
        return severity == AiPreCheckSeverity.WARNING;
    }

    public boolean isCriticalAssumption() {
        return type == AiPreCheckProblemType.CRITICAL_ASSUMPTION;
    }
}

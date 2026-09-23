package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckInputChange;

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
        boolean proposedChangeApplicable
) {
    public AiPreCheckProblemDto {
        suggestedUserAction = cleanSuggestedUserAction(suggestedUserAction);
    }

    public static String cleanSuggestedUserAction(String action) {
        if (action == null || action.isBlank()) {
            return action;
        }
        return action.replaceFirst("^(?i)(mögliche\\s+anpassungen|mögliche\\s+anpassung|anpassungen|vorschlag):\\s*", "").trim();
    }

    public boolean isOpenPoint() {
        return severity == AiPreCheckSeverity.WARNING;
    }

    public boolean isCriticalAssumption() {
        return type == AiPreCheckProblemType.CRITICAL_ASSUMPTION;
    }

    public String getTypeLabel() {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case CRITICAL_ASSUMPTION -> "Empfohlene Anpassung";
            case ASSUMPTION -> "Planungsannahme";
            case RISK -> "Planungsrisiko";
            case CONFLICT -> "Widerspruch";
        };
    }

    public String getActionLabel() {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case CRITICAL_ASSUMPTION -> "Vorgeschlagene Änderung übernehmen";
            case ASSUMPTION, RISK -> "Bestätigen";
            case CONFLICT -> null;
        };
    }
}

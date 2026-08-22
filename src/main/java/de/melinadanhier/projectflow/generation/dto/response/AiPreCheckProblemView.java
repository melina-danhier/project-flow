package de.melinadanhier.projectflow.generation.dto.response;

public record AiPreCheckProblemView(
        int index,
        AiPreCheckSeverity severity,
        String message,
        String suggestion
) {
    public boolean isWarning() {
        return severity == AiPreCheckSeverity.WARNING;
    }
}

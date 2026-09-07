package de.melinadanhier.projectflow.ai.model.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiPreCheckProblem(
        @NotNull AiPreCheckSeverity severity,
        @NotNull AiPreCheckProblemType type,
        @NotBlank @Size(max = 1000) String message,
        @NotBlank @Size(max = 1000) String suggestedUserAction,
        @NotNull @Size(max = 500) String reviewQuestion,
        @NotNull @Size(max = 1000) String acceptedInterpretation
) {
    public AiPreCheckProblem {
        message = message == null ? null : message.trim();
        suggestedUserAction = suggestedUserAction == null ? null : suggestedUserAction.trim();
        reviewQuestion = reviewQuestion == null ? null : reviewQuestion.trim();
        acceptedInterpretation = acceptedInterpretation == null ? null : acceptedInterpretation.trim();
    }

    public AiPreCheckProblem(
            AiPreCheckSeverity severity,
            AiPreCheckProblemType type,
            String message,
            String suggestedUserAction,
            String acceptedInterpretation
    ) {
        this(severity, type, message, suggestedUserAction,
                severity == AiPreCheckSeverity.WARNING
                        ? "Welche Planungsgrundlage soll gelten?"
                        : "",
                acceptedInterpretation);
    }

    /** Compatibility helper for deterministic server-side fixtures without a separate assumption type. */
    public AiPreCheckProblem(AiPreCheckSeverity severity, String message, String suggestedUserAction) {
        this(severity,
                severity == AiPreCheckSeverity.ERROR ? AiPreCheckProblemType.CONFLICT : AiPreCheckProblemType.RISK,
                message, suggestedUserAction,
                severity == AiPreCheckSeverity.ERROR ? "" : "Welche Planungsgrundlage soll gelten?",
                severity == AiPreCheckSeverity.ERROR ? "" : suggestedUserAction);
    }
}

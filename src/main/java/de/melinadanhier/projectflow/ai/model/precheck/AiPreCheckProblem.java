package de.melinadanhier.projectflow.ai.model.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPreCheckProblem(
        @NotNull AiPreCheckSeverity severity,
        @NotNull AiPreCheckProblemType type,
        @NotBlank @Size(max = 1000) String message,
        @NotBlank @Size(max = 1000) String suggestedUserAction,
        @NotNull @Size(max = 1000) String acceptedInterpretation,
        @NotNull @Size(max = 10) List<@Valid AiPreCheckInputChange> proposedInputChanges,
        @NotNull @Size(max = 3) List<AiPreCheckAdjustmentOption> adjustmentOptions
) {
    public AiPreCheckProblem {
        message = message == null ? null : message.trim();
        suggestedUserAction = suggestedUserAction == null ? null : suggestedUserAction.trim();
        acceptedInterpretation = acceptedInterpretation == null ? null : acceptedInterpretation.trim();
        proposedInputChanges = proposedInputChanges == null ? List.of() : List.copyOf(proposedInputChanges);
        adjustmentOptions = adjustmentOptions == null ? List.of() : List.copyOf(adjustmentOptions);
    }

    public AiPreCheckProblem(
            AiPreCheckSeverity severity,
            AiPreCheckProblemType type,
            String message,
            String suggestedUserAction,
            String acceptedInterpretation
    ) {
        this(severity, type, message, suggestedUserAction, acceptedInterpretation, List.of(), List.of());
    }

    /** Compatibility helper for deterministic server-side fixtures without a separate assumption type. */
    public AiPreCheckProblem(AiPreCheckSeverity severity, String message, String suggestedUserAction) {
        this(severity,
                severity == AiPreCheckSeverity.ERROR ? AiPreCheckProblemType.CONFLICT : AiPreCheckProblemType.RISK,
                message, suggestedUserAction,
                severity == AiPreCheckSeverity.ERROR ? "" : suggestedUserAction, List.of(), List.of());
    }

    public AiPreCheckProblem(
            AiPreCheckSeverity severity, AiPreCheckProblemType type, String message,
            String suggestedUserAction, String acceptedInterpretation,
            List<AiPreCheckInputChange> proposedInputChanges
    ) {
        this(severity, type, message, suggestedUserAction, acceptedInterpretation,
                proposedInputChanges, List.of());
    }
}

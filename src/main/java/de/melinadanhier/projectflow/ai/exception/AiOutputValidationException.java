package de.melinadanhier.projectflow.ai.exception;

import lombok.Getter;

/** Syntax-, Struktur- oder Versionsfehler einer externen KI-Antwort. */
@Getter
public class AiOutputValidationException extends AiTechnicalException {

    private final java.util.List<String> validationIssues;

    public AiOutputValidationException(String message) {
        this(message, null, defaultIssue());
    }

    public AiOutputValidationException(String message, Throwable cause) {
        this(message, cause, defaultIssue());
    }

    public AiOutputValidationException(String message, java.util.List<String> validationIssues) {
        this(message, null, validationIssues);
    }

    protected AiOutputValidationException(
            String message,
            Throwable cause,
            java.util.List<String> validationIssues
    ) {
        super(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, cause);
        this.validationIssues = java.util.List.copyOf(validationIssues);
    }

    private static java.util.List<String> defaultIssue() {
        return java.util.List.of("INVALID_AI_RESPONSE | $ | Die Antwort war nicht vollständig deserialisierbar.");
    }
}

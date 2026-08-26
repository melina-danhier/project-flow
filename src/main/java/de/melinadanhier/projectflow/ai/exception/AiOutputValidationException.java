package de.melinadanhier.projectflow.ai.exception;

/** Syntax-, Struktur- oder Versionsfehler einer externen AI-Antwort. */
public class AiOutputValidationException extends AiClientTechnicalException {

    private final java.util.List<String> validationIssues;

    public AiOutputValidationException(String message) {
        this(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, null, defaultIssue());
    }

    public AiOutputValidationException(String message, Throwable cause) {
        this(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, cause, defaultIssue());
    }

    public AiOutputValidationException(String message, java.util.List<String> validationIssues) {
        this(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, null, validationIssues);
    }

    protected AiOutputValidationException(
            AiTechnicalErrorCode errorCode,
            String message,
            Throwable cause
    ) {
        this(errorCode, message, cause, java.util.List.of());
    }

    protected AiOutputValidationException(
            AiTechnicalErrorCode errorCode,
            String message,
            Throwable cause,
            java.util.List<String> validationIssues
    ) {
        super(errorCode, message, cause, true);
        this.validationIssues = java.util.List.copyOf(validationIssues);
    }

    public java.util.List<String> getValidationIssues() {
        return validationIssues;
    }

    private static java.util.List<String> defaultIssue() {
        return java.util.List.of(
                "INVALID_AI_RESPONSE | $ | Die Antwort war nicht vollständig deserialisierbar.");
    }
}

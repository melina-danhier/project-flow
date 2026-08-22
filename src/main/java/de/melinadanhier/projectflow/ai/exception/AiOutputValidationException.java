package de.melinadanhier.projectflow.ai.exception;

/** Syntax-, Struktur- oder Versionsfehler einer externen AI-Antwort. */
public class AiOutputValidationException extends AiClientTechnicalException {

    public AiOutputValidationException(String message) {
        this(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, null);
    }

    public AiOutputValidationException(String message, Throwable cause) {
        this(AiTechnicalErrorCode.INVALID_AI_RESPONSE, message, cause);
    }

    protected AiOutputValidationException(
            AiTechnicalErrorCode errorCode,
            String message,
            Throwable cause
    ) {
        super(errorCode, message, cause, true);
    }
}

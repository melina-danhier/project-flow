package de.melinadanhier.projectflow.ai.exception;

import de.melinadanhier.projectflow.ai.model.AiOperation;

import java.util.Objects;

/** Vollständiger technischer Fehlerkontext für die Verarbeitung im KI-Workflow. */
public record AiTechnicalError(
        AiTechnicalErrorCode errorCode,
        AiOperation operation,
        String technicalMessage,
        Throwable cause
) {
    public AiTechnicalError {
        Objects.requireNonNull(errorCode, "errorCode darf nicht null sein");
        Objects.requireNonNull(operation, "operation darf nicht null sein");
        Objects.requireNonNull(technicalMessage, "technicalMessage darf nicht null sein");
        Objects.requireNonNull(cause, "cause darf nicht null sein");
        if (technicalMessage.isBlank()) {
            throw new IllegalArgumentException("technicalMessage darf nicht leer sein");
        }
    }

    public static AiTechnicalError from(RuntimeException exception, AiOperation operation) {
        Objects.requireNonNull(exception, "exception darf nicht null sein");
        Objects.requireNonNull(operation, "operation darf nicht null sein");

        AiTechnicalErrorCode errorCode = exception instanceof AiTechnicalException technicalException
                ? technicalException.getErrorCode()
                : AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
        String technicalMessage = exception.getMessage();
        if (technicalMessage == null || technicalMessage.isBlank()) {
            technicalMessage = exception.getClass().getSimpleName();
        }
        if (technicalMessage.isBlank()) {
            technicalMessage = "Unbekannter technischer KI-Fehler";
        }

        return new AiTechnicalError(errorCode, operation, technicalMessage, cause);
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    public String userMessage() {
        return errorCode.getUserMessage();
    }
}

package de.melinadanhier.projectflow.ai.exception;

import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.Getter;

/** Basisausnahme für technische Fehler eines AI-Clients. */
@Getter
public class AiClientTechnicalException extends GenerationException {

    private final boolean retryable;
    private final AiTechnicalErrorCode errorCode;

    public AiClientTechnicalException(String message) {
        this(AiTechnicalErrorCode.UNKNOWN_AI_ERROR, message, null, true);
    }

    public AiClientTechnicalException(String message, Throwable cause) {
        this(AiTechnicalErrorCode.UNKNOWN_AI_ERROR, message, cause, true);
    }

    public AiClientTechnicalException(String message, boolean retryable) {
        this(AiTechnicalErrorCode.UNKNOWN_AI_ERROR, message, null, retryable);
    }

    public AiClientTechnicalException(String message, Throwable cause, boolean retryable) {
        this(AiTechnicalErrorCode.UNKNOWN_AI_ERROR, message, cause, retryable);
    }

    public AiClientTechnicalException(
            AiTechnicalErrorCode errorCode,
            String message,
            Throwable cause,
            boolean retryable
    ) {
        super(message, cause);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode darf nicht null sein");
        this.retryable = retryable;
    }

}

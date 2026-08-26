package de.melinadanhier.projectflow.ai.exception;

import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.Getter;

/** Anbieterneutrale technische Ausnahme des KI-Workflows mit stabiler Fehleridentität. */
@Getter
public class AiTechnicalException extends GenerationException {

    private final AiTechnicalErrorCode errorCode;

    public AiTechnicalException(AiTechnicalErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AiTechnicalException(
            AiTechnicalErrorCode errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode darf nicht null sein");
    }
}

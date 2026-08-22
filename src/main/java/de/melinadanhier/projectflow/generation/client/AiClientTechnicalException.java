package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.common.exception.GenerationException;

/** Basisausnahme für technische Fehler eines AI-Clients. */
public class AiClientTechnicalException extends GenerationException {

    private final boolean retryable;

    public AiClientTechnicalException(String message) {
        this(message, null, true);
    }

    public AiClientTechnicalException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public AiClientTechnicalException(String message, boolean retryable) {
        this(message, null, retryable);
    }

    public AiClientTechnicalException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

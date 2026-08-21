package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.common.exception.GenerationException;

/** Basisausnahme für technische Fehler eines AI-Clients. */
public class AiClientTechnicalException extends GenerationException {

    public AiClientTechnicalException(String message) {
        super(message);
    }

    public AiClientTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}

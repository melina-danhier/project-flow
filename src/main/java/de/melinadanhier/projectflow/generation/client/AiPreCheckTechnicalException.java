package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.common.exception.GenerationException;

public class AiPreCheckTechnicalException extends GenerationException {

    public AiPreCheckTechnicalException(String message) {
        super(message);
    }

    public AiPreCheckTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}

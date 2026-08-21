package de.melinadanhier.projectflow.generation.client;

public class AiProviderUnavailableException extends AiClientTechnicalException {

    public AiProviderUnavailableException(String message) {
        super(message);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.melinadanhier.projectflow.generation.client;

/** Permanenter Provider-, Authentifizierungs- oder Request-Konfigurationsfehler. */
public class AiProviderConfigurationException extends AiClientTechnicalException {

    public AiProviderConfigurationException(String message) {
        super(message, false);
    }
}

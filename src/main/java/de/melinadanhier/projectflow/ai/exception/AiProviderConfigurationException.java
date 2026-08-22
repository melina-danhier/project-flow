package de.melinadanhier.projectflow.ai.exception;

/** Permanenter Provider-, Authentifizierungs- oder Request-Konfigurationsfehler. */
public class AiProviderConfigurationException extends AiClientTechnicalException {

    public AiProviderConfigurationException(String message) {
        super(AiTechnicalErrorCode.PROVIDER_CONFIGURATION_ERROR, message, null, false);
    }
}

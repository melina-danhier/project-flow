package de.melinadanhier.projectflow.ai.exception;

public class AiProviderUnavailableException extends AiClientTechnicalException {

    public AiProviderUnavailableException(String message) {
        super(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, message, null, true);
    }

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, message, cause, true);
    }
}

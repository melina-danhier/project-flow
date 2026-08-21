package de.melinadanhier.projectflow.generation.client;

/** Syntax-, Struktur- oder Versionsfehler einer externen AI-Antwort. */
public class AiOutputValidationException extends AiClientTechnicalException {

    public AiOutputValidationException(String message) {
        super(message);
    }

    public AiOutputValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.melinadanhier.projectflow.generation.client;

/** Technische Ablehnung einer inhaltlich zulässigen Provideranfrage. */
public class AiRequestRefusedException extends AiClientTechnicalException {

    public AiRequestRefusedException(String message) {
        super(message, false);
    }
}

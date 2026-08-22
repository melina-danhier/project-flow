package de.melinadanhier.projectflow.ai.exception;

/** Technische Ablehnung einer inhaltlich zulässigen Provideranfrage. */
public class AiRequestRefusedException extends AiClientTechnicalException {

    public AiRequestRefusedException(String message) {
        super(AiTechnicalErrorCode.AI_REFUSAL, message, null, false);
    }
}

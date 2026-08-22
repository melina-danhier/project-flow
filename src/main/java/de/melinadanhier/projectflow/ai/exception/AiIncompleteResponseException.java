package de.melinadanhier.projectflow.ai.exception;

/** Der Provider hat eine syntaktisch erkennbare, aber nicht abgeschlossene Antwort geliefert. */
public class AiIncompleteResponseException extends AiOutputValidationException {

    public AiIncompleteResponseException(String message) {
        super(AiTechnicalErrorCode.INCOMPLETE_AI_RESPONSE, message, null);
    }
}

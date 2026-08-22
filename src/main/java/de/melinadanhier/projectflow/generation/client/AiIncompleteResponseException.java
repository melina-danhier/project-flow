package de.melinadanhier.projectflow.generation.client;

/** Der Provider hat eine syntaktisch erkennbare, aber nicht abgeschlossene Antwort geliefert. */
public class AiIncompleteResponseException extends AiOutputValidationException {

    public AiIncompleteResponseException(String message) {
        super(message);
    }
}

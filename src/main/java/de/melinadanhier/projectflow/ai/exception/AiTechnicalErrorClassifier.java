package de.melinadanhier.projectflow.ai.exception;

import org.springframework.stereotype.Component;

@Component
public class AiTechnicalErrorClassifier {

    public AiTechnicalErrorCode classify(RuntimeException exception) {
        return exception instanceof AiClientTechnicalException technicalException
                ? technicalException.getErrorCode()
                : AiTechnicalErrorCode.UNKNOWN_AI_ERROR;
    }

    /** Returns a short provider-neutral diagnosis that is safe to persist and display. */
    public String diagnosis(AiTechnicalErrorCode code) {
        return switch (code) {
            case PROVIDER_UNAVAILABLE -> "Der KI-Anbieter war vorübergehend nicht erreichbar.";
            case PROVIDER_CONFIGURATION_ERROR -> "Der KI-Anbieter ist serverseitig nicht korrekt konfiguriert.";
            case INVALID_AI_RESPONSE -> "Die KI-Antwort entsprach nicht den erwarteten Planungsregeln.";
            case AI_REFUSAL -> "Der KI-Anbieter hat die unveränderte Anfrage abgelehnt.";
            case INCOMPLETE_AI_RESPONSE -> "Der KI-Anbieter hat keine vollständige Antwort geliefert.";
            case RETRY_INTERRUPTED -> "Die Wartezeit vor einem erneuten KI-Aufruf wurde unterbrochen.";
            case PRE_CHECK_INITIALIZATION_FAILED, PRE_CHECK_PROCESSING_FAILED ->
                    "Die technische KI-Vorprüfung konnte nicht abgeschlossen werden.";
            case UNKNOWN_AI_ERROR -> "Die KI-Verarbeitung ist an einem internen technischen Fehler gescheitert.";
        };
    }
}

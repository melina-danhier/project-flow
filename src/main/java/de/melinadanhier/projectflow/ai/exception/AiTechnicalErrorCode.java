package de.melinadanhier.projectflow.ai.exception;

import lombok.Getter;

/** Stabiler Fehlercode mit providerneutralen Metadaten für Fehler bei der KI-Ausführung. */
@Getter
public enum AiTechnicalErrorCode {
    PROVIDER_UNAVAILABLE(true,
            "Der KI-Anbieter war vorübergehend nicht erreichbar."
    ),
    PROVIDER_TIMEOUT(true,
            "Der KI-Anbieter hat nicht rechtzeitig geantwortet."
    ),
    RATE_LIMIT_EXCEEDED(true,
            "Das Aufruflimit des KI-Anbieters wurde vorübergehend erreicht."
    ),
    CLIENT_CONFIGURATION_ERROR(false,
            "Der KI-Zugriff ist serverseitig nicht korrekt konfiguriert."
    ),
    INVALID_AI_RESPONSE(false,
            "Die Antwort des KI-Anbieters entsprach nicht dem erwarteten Format oder den Planungsregeln."
    ),
    AI_REFUSAL(false,
            "Der KI-Anbieter hat die Anfrage abgelehnt."
    ),
    RETRY_INTERRUPTED(false,
            "Die Wartezeit vor einem erneuten KI-Aufruf wurde unterbrochen."
    ),
    UNKNOWN_AI_ERROR(false,
            "Die KI-Verarbeitung ist an einem internen technischen Fehler gescheitert."
    );

    private final boolean retryable;
    private final String userMessage;

    AiTechnicalErrorCode(boolean retryable, String userMessage) {
        this.retryable = retryable;
        this.userMessage = userMessage;
    }

}

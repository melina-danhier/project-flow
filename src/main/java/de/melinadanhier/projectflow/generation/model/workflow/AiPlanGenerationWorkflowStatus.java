package de.melinadanhier.projectflow.generation.model.workflow;

/**
 * Repräsentiert den Status des asynchronen KI-Workflows für Vorprüfung und Plangenerierung.
 */
public enum AiPlanGenerationWorkflowStatus {
    PRE_CHECK_PENDING,
    PRE_CHECK_RUNNING,
    PRE_CHECK_RETRY_PENDING,
    /**
     * Vorprüfung erfolgreich abgeschlossen (keine Probleme gefunden oder alle Warnungen bestätigt)
     */
    PRE_CHECK_COMPLETED,
    /**
     * Vorprüfung abgeschlossen, es liegen jedoch noch unbestätigte Warnungen oder blockierende Fehler vor.
     * Bei blockierenden Fehlern: Die Generierung ist gesperrt, bis der Nutzer die Hinweise
     * geprüft und bestätigt oder korrigiert hat.
     * Bei Warnungen: Die Generierung ist freigegeben, der Nutzer kann die Warnungen ignorieren
     * und die Generierung starten oder seine Eingaben korrigieren.
     */
    PRE_CHECK_NEEDS_REVIEW,
    /**
     * Vorprüfung wurde während der Ausführung abgebrochen.
     */
    PRE_CHECK_CANCELLED,
    GENERATION_PENDING,
    GENERATION_RUNNING,
    /**
     * Laufende Plangenerierung wurde abgebrochen. Das zuvor erfolgreiche Vorprüfungsergebnis bleibt
     * gültig, sodass der Nutzer in der Review-Ansicht wahlweise die Generierung neu starten oder
     * zu den Wizard-Eingaben zurückkehren kann.
     */
    GENERATION_CANCELLED,
    ASSUMPTIONS_REVIEW_PENDING,
    GENERATION_COMPLETED,
    GENERATION_FAILED,
    TECHNICAL_FAILURE;

    public boolean isActiveExecution() {
        return switch (this) {
            case PRE_CHECK_PENDING, PRE_CHECK_RUNNING, PRE_CHECK_RETRY_PENDING,
                    GENERATION_PENDING, GENERATION_RUNNING -> true;
            default -> false;
        };
    }

    public boolean isPreCheckPhase() {
        return switch (this) {
            case PRE_CHECK_PENDING, PRE_CHECK_RUNNING, PRE_CHECK_RETRY_PENDING,
                 PRE_CHECK_COMPLETED, PRE_CHECK_NEEDS_REVIEW, PRE_CHECK_CANCELLED -> true;
            default -> false;
        };
    }
}

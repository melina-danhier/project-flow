package de.melinadanhier.projectflow.ai.validation.generation;

import lombok.Getter;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;

/** Zentrale Fehlerarten der Planvalidierung. Namen bleiben als technische Codes stabil. */
@Getter
public enum GenerationValidationCode {

    RESPONSE_MISSING("Es wurde kein Projektplan erzeugt."),
    REQUEST_MISSING("Die zugehörige Generierungsanfrage fehlt."),
    WIZARD_DATA_MISSING("Die bestätigten Wizard-Daten fehlen."),
    BEAN_VALIDATION_FAILED("Eine Angabe verletzt die Validierungsregeln."),
    CRITICAL_ASSUMPTIONS_MISSING("Die globale Liste kritischer Annahmen fehlt."),
    CRITICAL_ASSUMPTION_INVALID("Eine kritische Annahme fehlt oder ist leer."),
    CRITICAL_ASSUMPTION_DUPLICATE("Eine kritische Annahme wird mehrfach ausgegeben."),

    SECTION_MISSING("Es wurde kein Bereich erzeugt."),
    SECTION_INVALID("Der Plan enthält einen leeren Bereich."),
    SECTION_TITLE_MISSING("Ein Bereichstitel fehlt."),
    SECTION_DESCRIPTION_BLANK("Eine vorhandene Bereichsbeschreibung darf nicht leer sein."),
    SECTION_ORDER_INVALID("Eine Bereichsreihenfolge ist nicht positiv."),
    SECTION_ORDER_DUPLICATE("Eine Bereichsreihenfolge wird mehrfach verwendet."),
    SECTION_TASK_MISSING("Jeder Bereich muss mindestens eine Aufgabe enthalten."),
    SECTION_LIMIT_EXCEEDED("Der Plan enthält mehr als " + MAX_SECTIONS + " Bereiche."),

    TASKS_MISSING("Bei einem Bereich fehlt die Aufgaben-Liste."),
    TASK_MISSING("Es wurde keine Aufgabe erzeugt."),
    TASK_INVALID("Der Plan enthält eine leere Aufgabe."),
    TASK_TITLE_MISSING("Ein Aufgabentitel fehlt."),
    TASK_DESCRIPTION_BLANK("Eine vorhandene Aufgabenbeschreibung darf nicht leer sein."),
    TASK_ORDER_INVALID("Eine Aufgabenreihenfolge ist nicht positiv."),
    TASK_ORDER_DUPLICATE("Eine Aufgabenreihenfolge wird innerhalb eines Bereichs mehrfach verwendet."),
    TASK_ORIGIN_MISSING("Bei einer Aufgabe fehlt die Herkunft."),
    TASK_EFFORT_INVALID("Ein Aufgabenaufwand muss zwischen 1 und " + MAX_ESTIMATED_HOURS + " Stunden liegen."),
    TASK_COUNT_TOO_LOW("Der Plan muss mindestens " + MIN_TASKS + " Aufgaben enthalten."),
    TASK_LIMIT_EXCEEDED("Der Plan enthält mehr als " + MAX_TASKS + " Aufgaben."),

    MILESTONES_MISSING("Bei einem Bereich fehlt die Meilenstein-Liste."),
    MILESTONE_INVALID("Der Plan enthält einen leeren Meilenstein."),
    MILESTONE_TITLE_MISSING("Ein Meilensteintitel fehlt."),
    MILESTONE_ORDER_INVALID("Eine Meilensteinreihenfolge ist nicht positiv."),
    MILESTONE_ORDER_DUPLICATE("Eine Meilensteinreihenfolge wird innerhalb eines Bereichs mehrfach verwendet."),
    MILESTONE_LIMIT_EXCEEDED("Der Plan enthält mehr als " + MAX_MILESTONES + " Meilensteine."),

    TASK_DUE_DATE_MISSING("Bei terminierter Planung benötigt jede Aufgabe ein Fälligkeitsdatum."),
    TASK_DATES_INVALID("Eine Aufgabe beginnt nach ihrem Fälligkeitsdatum."),
    TASK_DATE_OUTSIDE_PROJECT("Ein Aufgabentermin liegt außerhalb des Projektzeitraums."),
    MILESTONE_DATE_MISSING("Bei terminierter Planung benötigt jeder Meilenstein ein Datum."),
    MILESTONE_DATE_OUTSIDE_PROJECT("Ein Meilenstein Termin liegt außerhalb des Projektzeitraums."),

    TEMP_ID_MISSING("Bei einem Planelement fehlt die temporäre ID."),
    TEMP_ID_DUPLICATE("Die temporäre ID wird mehrfach verwendet."),
    DEPENDENCIES_MISSING("Die Abhängigkeitsliste fehlt."),
    DEPENDENCY_DUPLICATE("Dieselbe gerichtete Abhängigkeit ist mehrfach angegeben."),
    SELF_DEPENDENCY("Eine Aufgabe darf nicht von sich selbst abhängen."),
    UNKNOWN_TASK_REFERENCE("Die Abhängigkeit verweist auf keine vorhandene Aufgabe."),
    DEPENDENCY_DATE_ORDER_INVALID("Die Aufgabe beginnt oder endet vor der Deadline ihrer Voraussetzung."),
    DEPENDENCY_CYCLE("Die Aufgabenabhängigkeiten enthalten einen Zyklus."),
    DEPENDENCY_LIMIT_EXCEEDED("Der Plan enthält mehr als " + MAX_DEPENDENCIES + " Abhängigkeiten.");

    private final String defaultMessage;

    GenerationValidationCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
}

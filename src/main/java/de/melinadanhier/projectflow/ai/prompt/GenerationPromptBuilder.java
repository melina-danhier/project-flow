package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerationPromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS_TEMPLATE = """
            Erzeuge ausschließlich aus den nachfolgend getrennt übergebenen, vom Nutzer bestätigten
            Wizard-Daten einen strukturierten PlanDraft. Erfinde keine Nutzerinformationen.

            Regeln:
            - Gib keine bereits bestätigten allgemeinen Projektdaten zurück, insbesondere keinen
              Projekttitel, keine Kategorie, Unterkategorie oder Projektart.
            - Erzeuge ausschließlich Sections mit Aufgaben und Meilensteinen. Eine Section ist ein
              allgemeiner Bereich und kann zeitlich, thematisch oder funktional gegliedert sein.
              Sections besitzen deshalb keine eigenen Datumsfelder.
            - Richte Detailtiefe, Aufgabenumfang und Komplexität am konkreten Projektkontext aus,
              insbesondere an Projektgröße, Einzel- oder Gruppenmodus, Zeitraum, Beteiligten und genannten
              Rahmenbedingungen. Überplane kleine, private oder studentische Vorhaben nicht.
              Ein einfaches, klar begrenztes und risikoarmes Vorhaben benötigt typischerweise nur
              eine bis drei Sections, ungefähr fünf bis zehn substanzielle Aufgaben und keinen oder
              höchstens einen wirklich aussagekräftigen Meilenstein. Dies ist ein Orientierungswert,
              kein Mindestumfang; komplexere bestätigte Anforderungen dürfen mehr Struktur erhalten.
              Fasse eng zusammengehörige Arbeitsschritte zusammen, statt sie künstlich in Prüf-,
              Vorbereitungs-, Dokumentations- oder Abschlussaufgaben zu zerlegen.
            - Erzeuge insgesamt mindestens drei Aufgaben.
            - Gib alle im Ausgabeschema definierten Felder zurück. Nutze für nicht belegte optionale
              Werte null statt das Feld wegzulassen.
            - Vergib für jede Aufgabe einen im gesamten Entwurf eindeutigen, stabilen tempId-Wert.
              Er ist eine Referenz im Entwurf und keine Datenbank-ID. Bei Sections und Meilensteinen
              darf tempId null sein.
            - Gib für jede Aufgabe prerequisiteTaskTempIds als Liste vorhandener Aufgaben-tempId-Werte
              zurück. Nutze eine leere Liste, wenn keine Abhängigkeiten bestehen. Erzeuge weder
              Selbstabhängigkeiten noch Zyklen.
            - priority ist optional und darf nur LOW, MEDIUM oder HIGH sein. Setze den Wert auf null,
              wenn keine begründete Priorität ableitbar ist.
            - estimatedHours ist optional. Setze den Wert auf null, wenn wesentliche Angaben zu Menge,
              Umfang oder Ausgangslage für eine belastbare Schätzung fehlen. Wenn eine Schätzung
              hinreichend begründet ist, verwende eine konservative, grobe ganze Stundenzahl ohne
              scheinbare Präzision. Überschätze kleine organisatorische Tätigkeiten nicht.
              Schätze den Aufwand für jede Aufgabe, sobald Ziel, Umfang und Ausgangslage dafür eine
              sinnvolle grobe Schätzung erlauben. Betrachte die Summe aller Aufgaben als geschätzten
              Gesamtaufwand des Plans und prüfe sie gegen die bestätigte Gesamtdauer und Arbeitszeit.
              Richte die Terminierung an einer angegebenen Tageskapazität aus. Plane an keinem Tag
              offensichtlich mehr geschätzte Aufgabenstunden ein als verfügbar sind und verdichte
              Aufgaben nicht künstlich, nur um einen unrealistischen Zeitraum einzuhalten.
            - origin ist genau USER_INPUT, wenn der Inhalt unmittelbar aus einer Nutzereingabe folgt,
              andernfalls AI_INFERRED.
            - Gib keinen Prüfstatus wie checked, verified oder reviewed zurück. Neue Inhalte sind
              anwendungsseitig ungeprüft.
            - Startdatum, Enddatum, Dauer und verfügbare Arbeitszeit sind voneinander unabhängige Angaben.
              Bestätigte Nutzereingaben haben Vorrang und dürfen weder überschrieben noch umgedeutet werden.
              Konkrete Kalenderdaten dürfen nur verwendet werden, wenn sie aus confirmedWizardData oder
              einer akzeptierten Zeitschätzung in confirmedPlanningContext plausibel ableitbar sind.
              Bei einem terminierten Projekt dürfen Aufgaben konkrete Termine erhalten. Wenn konkrete
              Termine vergeben werden, müssen sie vollständig konsistent innerhalb des Projektzeitraums liegen.
              Aufgaben ohne notwendige zeitliche Bindung dürfen ohne Fälligkeitsdatum bleiben.
              Ergänze keine fehlenden Datumswerte durch bloße technische Annahmen. Ohne ausreichend
              konkrete Grundlage bleiben Aufgaben-startDate, Aufgaben-dueDate und Meilenstein-date null.
            - Verwende in Datumsfeldern ausschließlich konkrete Kalenderdaten im vorgesehenen ISO-Format
              oder null. Relative Werte wie „Woche 1“, „Tag 5“ oder „drei Tage nach Projektstart“ sind
              dort verboten. Sections dürfen bei fachlichem Nutzen zeitlich benannte Titel wie
              „Woche 1 – Grundlagen“ tragen; erzwinge solche Wochenphasen nicht für jedes Projekt.
            - Meilensteine sind ausschließlich wichtige erreichte Zustände, Ergebnisse oder Ereignisse,
              die einen relevanten Fortschritt im Projekt markieren. Formuliere sie als eingetretenen
              Zustand oder erreichtes Ergebnis, nicht als auszuführende Tätigkeit. Tätigkeiten gehören
              als Aufgaben in den Plan. Erzeuge nur Meilensteine, die im konkreten Projekt sinnvoll sind.
            - Decke alle wesentlichen Teilbereiche ab, die zum Erreichen des bestätigten Projektziels
              erforderlich und aus den bestätigten Angaben ableitbar sind. Prüfe vor der Ausgabe den
              gesamten Entwurf auf diese inhaltliche Vollständigkeit und ergänze fehlende Sections,
              Aufgaben oder Meilensteine.
            - Decke jedes ausdrücklich bestätigte Endergebnis durch mindestens eine konkrete Aufgabe ab,
              durch die dieses Ergebnis tatsächlich hergestellt, durchgeführt oder fertiggestellt wird.
              Eine reine Prüfung, Abstimmung oder ein Meilenstein ersetzt diese Umsetzungsaufgabe nicht.
            - Bleibe eng beim ausdrücklich bestätigten Projektziel. Ergänze keine neuen Teilziele,
              Verbesserungen oder optionalen Vorgehensweisen als feste Aufgaben. Insbesondere dürfen
              Verwertung, Weitergabe, Reparatur, Fotodokumentation oder zusätzliche Anschaffungen nur
              als Aufgabe erscheinen, wenn sie bestätigt oder zur Zielerreichung zwingend erforderlich
              sind. Ein lediglich denkbarer oder üblicher Weg ist nicht automatisch erforderlich.
              Bewahre sprachliche Einschränkungen und ihren Bezugszeitraum: Verallgemeinere Angaben
              wie „aktuell“, „vorerst“, „bis“ oder „während“ nicht zu dauerhaften Voraussetzungen.
            - Erfinde keine konkreten Entscheidungen, Ressourcen, Mengen, Personen, Präferenzen oder
              sonstigen Nutzerinformationen. Wenn eine fehlende Information für die spätere Umsetzung
              geklärt werden muss, lasse den betroffenen optionalen Wert null oder plane eine angemessen
              formulierte Aufgabe zur Klärung bzw. Entscheidung ein. Behaupte kein erfundenes Ergebnis.
              Nenne insbesondere keine konkreten Anbieter, Produkte, Personen oder Zuständigkeiten,
              sofern sie nicht bestätigt wurden.
            - Erzeuge keine detaillierte Personal- oder Ressourceneinsatzplanung, wenn sie nicht ausdrücklich
              verlangt wurde. Plane bei Gruppenprojekten bei Bedarf eine kompakte organisatorische Aufgabe
              wie „Zuständigkeiten verteilen“, statt unbestätigte Personen einzelnen Aufgaben zuzuweisen.
            - Platziere einen Meilenstein logisch und bei terminierter Planung auch zeitlich erst nach allen
              Aufgaben, durch die sein Zustand erreicht wird. Sein Datum darf nicht vor dem Fälligkeitsdatum
              einer dafür erforderlichen Aufgabe liegen.
              Bei einer Planung mit festgelegtem Start- und Enddatum muss jeder erzeugte Meilenstein ein
              konkretes Datum innerhalb des Projektzeitraums besitzen. Das Datum muss nach allen Aufgaben liegen,
              durch die der Meilenstein erreicht wird. Wenn kein sinnvoller Meilenstein mit einem plausiblen
              Datum bestimmt werden kann, soll kein Meilenstein erzeugt werden.
            - Tasks und Milestones innerhalb einer Section teilen sich denselben Nummernkreis für das Feld
              order und bilden gemeinsam eine eindeutige Reihenfolge (z. B. Task 100, Task 200, Milestone 300,
              Task 400). Verwende als Konvention 100er-Schritte. Jeder order-Wert innerhalb einer Section muss
              über alle Aufgaben und Meilensteine hinweg eindeutig und positiv sein.
            - Interpretiere bei einem Projekt mit eindeutigem Abschlussereignis den letzten bestätigten
              Projekttag standardmäßig als Tag dieses Ereignisses, sofern die bestätigten Angaben nichts
              anderes sagen. Plane keine Nachbereitung außerhalb des bestätigten Zeitraums und verlängere
              diesen niemals stillschweigend. Nachbereitung gehört nur in den Plan, wenn sie ausdrücklich
              zum Ziel oder Scope gehört, und muss dann innerhalb des bestätigten Zeitraums liegen.
            - Priorisiere bei Konflikten in dieser Reihenfolge: confirmedWizardData, danach ergänzender
              confirmedPlanningContext, danach Hinweise aus previousOutputValidationIssues und zuletzt
              die übrigen Regeln dieses Prompts.
            - Die vom Nutzer bestätigten Planungsgrundlagen in confirmedPlanningContext sind verbindlich.
              Verwende jede acceptedInterpretation als Grundlage der Planung, hinterfrage oder interpretiere
              sie nicht erneut und gib sie nicht als neue offene Annahme zurück. Sie dürfen bestätigte
              Nutzereingaben nur ergänzen, niemals ersetzen.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(confirmedSnapshot, List.of());
    }

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot, List<AiPreCheckProblem> acceptedOpenPoints) {
        return build(new AiGenerationRequest(confirmedSnapshot, acceptedOpenPoints));
    }

    public AiPrompt build(AiGenerationRequest request) {
        return new AiPrompt(
                AiPromptVersions.GENERATION_PROMPT,
                SYSTEM_INSTRUCTIONS_TEMPLATE,
                serializeRequestData(request)
        );
    }

    private String serializeRequestData(AiGenerationRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", request.confirmedWizardData());
        context.put("confirmedPlanningContext", request.acceptedOpenPoints().stream()
                .map(AiPreCheckProblem::acceptedInterpretation)
                .toList());
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JacksonException exception) {
            throw new GenerationException(
                    "Die bestätigten Wizard-Daten konnten nicht für die Generierung aufbereitet werden.",
                    exception
            );
        }
    }
}

package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PlanChangePromptBuilder {
    private final ObjectMapper objectMapper;

    public AiPrompt build(AiPlanChangeRequest request) {
        String instructions = """
                Prüfe zuerst, ob der Änderungswunsch fachlich zum Projektziel oder zum aktuellen Projektplan gehört.
                Zulässig ist er nur, wenn er das Projektziel direkt unterstützt, einen bestehenden Planteil sinnvoll
                verändert oder ergänzt oder eine nachvollziehbare Projektaktivität hinzufügt. Konstruiere keine nur
                indirekte oder künstliche Verbindung, um einen fachfremden Wunsch dennoch zu erfüllen. Eine allgemeine
                Dehnroutine gehört beispielsweise nicht allein deshalb in einen Umzugsplan, weil ein Umzug körperlich
                anstrengend ist; ein Aufsatz über Klimawandel gehört ohne entsprechenden Projektbezug ebenfalls nicht
                hinein. Bei Unsicherheit lehne eher ab, statt fachfremde Inhalte einzubauen.
                Setze applicability=NOT_APPLICABLE, gib eine kurze nutzerbezogene rejectionReason an und liefere leere
                Änderungslisten, wenn der Wunsch außerhalb des Projekts liegt. Setze sonst applicability=APPLICABLE,
                rejectionReason=null und erzeuge den nachfolgenden Diff mit mindestens einer tatsächlichen Änderung.
                Verwende summary oder explanation niemals als rejectionReason. NOT_APPLICABLE ist eine fachliche
                Entscheidung, kein technischer Fehler.
                Schlage ausschließlich die für den Änderungswunsch notwendigen Änderungen am vorhandenen Plan vor.
                Gib einen Diff und niemals einen vollständigen Ersatzplan zurück. Keine Löschungen. Erfinde keine IDs.
                Interpretiere den Änderungswunsch ausschließlich als Kombination der unterstützten Operationen
                ADD, MODIFY, MOVE und REPLAN. ADD erzeugt neue Sections, Tasks oder Milestones. MODIFY ändert nur
                die im Schema freigegebenen fachlichen Felder. MOVE ändert nur Section-Zuordnung oder relative
                Position. REPLAN ändert Start-/Fälligkeitsdaten, die fachlich passende Reihenfolge und nur wenn
                dafür erforderlich die Section-Zuordnung. Erfinde keine weiteren Operationen.
                Nicht unterstützt sind: bestehende Elemente löschen, den ganzen Plan ersetzen oder neu generieren,
                Dependencies oder Assignees automatisch ändern, Completion-State ändern sowie technische oder
                sonstige nicht freigegebene Felder. Deute solche Wünsche nicht kreativ in eine erlaubte Operation um.
                Wenn ein Wunsch nicht sinnvoll vollständig mit ADD, MODIFY, MOVE und REPLAN abbildbar ist, antworte
                mit NOT_APPLICABLE, leeren Änderungslisten und einer kurzen nutzerverständlichen rejectionReason.
                Beispiele dafür sind: "Lösche alle bisherigen Aufgaben", "Ersetze den gesamten Plan durch einen
                besseren", "Ändere automatisch alle Abhängigkeiten" sowie fachfremde Wünsche.
                Setze alle ausdrücklich gewünschten, fachlich anwendbaren Änderungen gemeinsam in genau einem Diff um.
                Mehrere Änderungen, mehrere neue Elemente und Änderungen über mehrere Sections sind ausdrücklich
                erlaubt und dürfen nicht auf eine Section oder ein Element reduziert werden.
                MODIFIED referenziert genau eine vorhandene ID aus currentPlan; NEW hat keine bestehende ID.
                changedFields nennt exakt die fachlichen Felder, die sich ändern. Nicht genannte Felder bleiben null.
                Verwende in changedFields ausschließlich die im Schema vorgegebenen, exakt geschriebenen Werte.
                Bei NEW sind existingSectionId, existingTaskId beziehungsweise existingMilestoneId immer null.
                Das Placement-Objekt unterstützt ausschließlich beforeElementId und afterElementId. Wenn eine relative
                Position geändert wird, setze exakt eine dieser beiden Referenzen auf eine gültige Element-reference
                und die jeweils andere auf null. Verwende keine Synonyme oder Werte wie start, end, first, last,
                beforeElement oder afterElement. Wenn keine Positionsänderung erforderlich ist, setze placement auf
                ein Objekt mit beforeElementId=null und afterElementId=null und nenne position nicht in changedFields.
                targetSectionId bleibt bei MODIFIED null, solange die Section nicht geändert wird; bei einer
                Section-Änderung und bei NEW enthält es eine gültige vorhandene Section-ID oder die Referenz einer
                im selben Diff neu vorgeschlagenen Section.
                Löse Section-Angaben im Änderungswunsch semantisch gegen currentPlan.sections auf. Verlange keine
                exakte Schreibweise: Berücksichtige Rechtschreibfehler, verkürzte Titel und Umschreibungen wie
                „die Section über den Transport“. Bei Formulierungen wie „in den passenden Bereich“ wählst du anhand
                von Titel, Beschreibung und enthaltenen Planelementen die fachlich passendste vorhandene Section.
                Gib danach ausschließlich deren reference als targetSectionId zurück, niemals den Section-Titel.
                Erzeuge keine neue Section, wenn eine vorhandene Section den erkennbaren Nutzerwunsch erfüllt.
                Ist eine Zuordnung tatsächlich mehrdeutig, nimm keine unbegründeten Änderungen an mehreren Sections
                vor, sondern wähle höchstens die nach Planinhalt plausibelste Zuordnung.
                Erlaubt: Sections title, description, position; Tasks title, description, priority, estimatedHours,
                startDate, dueDate, section, position; Milestones title, description, dueDate, section, position.
                Verwende für Positionen nur before/after-Referenzen, niemals sortOrder. Referenzen müssen im Zielcontainer
                liegen. Completion-State, Status, Zuständigkeiten, Abhängigkeiten und technische Felder bleiben unverändert.
                Neue Elemente müssen mindestens Titel und Ziel-Section angeben; neue Tasks außerdem priority.
                Bewahre den übrigen Plan. summary und optionale explanations sind kurz, nutzerbezogen und enthalten
                keine internen Gedankengänge, IDs oder technischen Feldnamen.

                Eindeutige Ergebnisfälle:
                APPLICABLE: rejectionReason ist null; mindestens sections, tasks oder milestones enthält eine Änderung.
                Beispiel: Zwei gewünschte Aufgaben in zwei vorhandenen Sections ergeben zwei NEW-Einträge in tasks.
                NOT_APPLICABLE: sections, tasks und milestones sind leer; rejectionReason enthält den kurzen Ablehnungsgrund.
                """;
        try {
            return new AiPrompt(AiPromptVersions.PLAN_CHANGE_PROMPT, instructions,
                    objectMapper.writeValueAsString(request));
        } catch (JacksonException exception) {
            throw new GenerationException("Der Projektplan konnte nicht für die KI aufbereitet werden.", exception);
        }
    }
}

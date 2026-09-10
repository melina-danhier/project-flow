package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementTemporalContext;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImprovementPromptBuilder {

    private static final String COMMON_INSTRUCTIONS = """
            Bearbeite genau das ausgewählte Planelement gemäß der Aktion. Der optionale Kommentar darf die
            Aktion konkretisieren, aber niemals ihre Feldfreigabe erweitern. Gib ausschließlich die im jeweiligen
            Ausgabeschema enthaltenen Felder zurück. Der übrige Kontext ist nur lesend und darf nicht verändert
            werden. Erfinde keine Personen, Zuständigkeiten, Abhängigkeiten oder Projektinformationen.
            Titel dürfen höchstens 100 Zeichen, Beschreibungen höchstens 2000 Zeichen lang sein.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiImprovementRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", request.feedbackType());
        data.put("comment", request.comment());
        data.put("elementType", request.element().elementType());
        data.put("element", elementData(request));
        if (request.project() != null) data.put("project", request.project());
        if (request.section() != null) data.put("section", request.section());
        if (request.plan() != null) data.put("currentPlan", request.plan());
        if (request.feedbackType() == AiFeedbackType.REPLAN) {
            data.put("temporalContextAvailable", AiImprovementTemporalContext.isAvailable(request));
        }
        try {
            return new AiPrompt(AiPromptVersions.IMPROVEMENT_PROMPT,
                    COMMON_INSTRUCTIONS + actionInstructions(request.feedbackType()),
                    objectMapper.writeValueAsString(data));
        } catch (JacksonException exception) {
            throw new GenerationException("Das Planelement konnte nicht für die KI aufbereitet werden.", exception);
        }
    }

    private Map<String, Object> elementData(AiImprovementRequest request) {
        AiImprovementContent element = request.element();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", element.title());
        data.put("description", element.description());
        if (request.feedbackType() == AiFeedbackType.REPLAN) {
            if (element.elementType() == AiImprovementElementType.TASK) data.put("startDate", element.startDate());
            data.put("dueDate", element.dueDate());
        } else if (request.feedbackType() == AiFeedbackType.ESTIMATE_EFFORT) {
            data.put("estimatedHours", element.estimatedHours());
        }
        return data;
    }

    private String actionInstructions(AiFeedbackType action) {
        return switch (action) {
            case IMPROVE -> """

                    IMPROVE: Glätte Sprache und verbessere Klarheit, Lesbarkeit und Formulierung. Behalte Bedeutung,
                    Informationsgehalt und ungefähre Länge bei. Gib ausschließlich title und description zurück.
                    """;
            case EXPAND -> """

                    EXPAND: Konkretisiere vorhandene Inhalte und ergänze sinnvolle Details. Die Beschreibung darf
                    merklich länger werden. Gib ausschließlich title und description zurück.
                    """;
            case SIMPLIFY -> """

                    SIMPLIFY: Entferne Redundanzen und formuliere kürzer und leichter verständlich. Füge keine neuen
                    fachlichen Inhalte hinzu. Gib ausschließlich title und description zurück.
                    """;
            case REPLAN -> """

                    REPLAN: Bewerte Termine, Ziel-Section und relative Position des ausgewählten Elements anhand des
                    vollständigen aktuellen Plans fachlich neu und unabhängig von seiner bisherigen Platzierung.
                    currentPlan.selectedElementReference kennzeichnet das ausgewählte Element eindeutig.
                    currentPlan.sections[].elements enthält Tasks und Milestones gemeinsam in der aktuell sichtbaren,
                    durch position eindeutig angegebenen Reihenfolge. Prüfe ausdrücklich, welche Tasks logisch vor
                    oder nach anderen Tasks und Milestones liegen müssen. Milestones beschreiben zu erreichende
                    Zustände oder Zwischenergebnisse. Eine Aufgabe, die einen solchen Zustand herstellt, muss vor dem
                    betreffenden Milestone stehen, insbesondere bei gleichem Fälligkeitsdatum. Übernimm die bisherige
                    Section oder Position nicht als Default, wenn sie fachlich unpassend ist. Ist sie sinnvoll, darf
                    sie unverändert bleiben. Gib bei einer Aufgabe startDate und dueDate, bei einem Meilenstein nur
                    dueDate zurück. Behalte bestehende null-Datumswerte als null bei, wenn keine belastbare zeitliche
                    Grundlage vorliegt. temporalContextAvailable=false bedeutet ausdrücklich, dass du keine neuen
                    Datumswerte ergänzen darfst. Ändere oder ergänze ein Datum nur, wenn es aus vorhandenen
                    Projektzeitangaben, einer expliziten zeitlichen Nutzerangabe, datierten Abhängigkeiten oder anderem
                    konkreten Zeitkontext ableitbar ist. Eine Änderung von Section oder Reihenfolge allein begründet
                    keine Datumsänderung. Erfinde niemals absolute Datumswerte. Gib niemals eine sortOrder zurück.
                    Verwende ausschließlich Section- und
                    Element-Referenzen aus currentPlan. Setze placement.changePlacement=false und alle Placement-IDs
                    auf null, wenn nach dieser Prüfung keine Platzierungsänderung nötig ist. Bei einer Änderung bezeichnet
                    targetSectionId die Ziel-Section; null steht für „ohne Section“. Setze höchstens eine der Angaben
                    beforeElementId oder afterElementId. Beide müssen auf ein anderes Element der Ziel-Section zeigen.
                    Wähle bei einer Neuplatzierung möglichst ein konkretes fachlich passendes Referenzelement und setze
                    entsprechend beforeElementId oder afterElementId. Lass beide nur dann null, wenn das Ende der
                    Ziel-Section beziehungsweise Datumsgruppe tatsächlich die richtige Position ist oder dort kein
                    sinnvolles Referenzelement existiert. Im Sortiermodus DATE ist eine relative Referenz nur bei
                    gleichem Fälligkeitsdatum sinnvoll; das Datum bleibt die primäre Sortierung.
                    Ergänze explanation mit einer kurzen, nutzerbezogenen Begründung (maximal 500 Zeichen), warum
                    die beibehaltene oder vorgeschlagene zeitliche Planung sinnvoll ist. Wenn kein belastbarer
                    Zeitkontext vorhanden ist, begründe keine erfundenen Termine, sondern nur die fachliche
                    Neuplatzierung. Wenn du Section oder relative Position änderst, nenne darin zusätzlich knapp den
                    fachlichen Grund für diese Einordnung. Verwende in der Erklärung keine
                    IDs, Referenzfelder, sortOrder oder technischen Positionsangaben wie beforeElementId und
                    afterElementId. Verändere keine anderen Elemente, Sections, Abhängigkeiten oder sonstigen Felder.
                    Beschreibe nur das Ergebnis und fordere keine internen Gedankengänge oder Herleitungsschritte an.
                    """;
            case ESTIMATE_EFFORT -> """

                    ESTIMATE_EFFORT: Schätze den Arbeitsaufwand der ausgewählten Aufgabe in positiven ganzen Stunden.
                    Gib estimatedHours und explanation mit einer kurzen, nutzerbezogenen Begründung (maximal 500
                    Zeichen) zurück. Erkläre knapp anhand von Aufgabe und Kontext, warum der Aufwand sinnvoll ist.
                    Beschreibe nur das Ergebnis und fordere keine internen Gedankengänge oder Herleitungsschritte an.
                    """;
        };
    }
}

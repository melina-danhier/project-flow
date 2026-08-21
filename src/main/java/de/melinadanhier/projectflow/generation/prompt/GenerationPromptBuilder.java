package de.melinadanhier.projectflow.generation.prompt;

import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
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
            - Erzeuge ausschließlich Phasen mit Aufgaben und Meilensteinen.
            - Vergib innerhalb des Entwurfs eindeutige, stabile tempId-Werte. Sie sind Referenzen im
              Entwurf und keine Datenbank-IDs.
            - origin ist genau USER_INPUT, wenn der Inhalt unmittelbar aus einer Nutzereingabe folgt,
              andernfalls AI_INFERRED.
            - Gib keinen Prüfstatus wie checked, verified oder reviewed zurück. Neue Inhalte sind
              anwendungsseitig ungeprüft.
            - Plane Termine konsistent: entweder nachvollziehbar mit Terminen oder vollständig ohne
              konkrete Termine. Wenn die Eingaben keine belastbare Terminplanung erlauben, lasse alle
              Datumsfelder weg. Mische datierte und undatierte vergleichbare Elemente nicht zufällig.
            - Berücksichtige bewusst ignorierte Warnungen nur als Kontext; ändere deswegen keine Eingabe.
            - Antworte ausschließlich mit einem JSON-Objekt des Schemas %s, ohne Markdown oder Freitext.
              Erforderliche Struktur:
              {"schemaVersion":"%s","metadata":{"summary":"...","assumptions":[]},
              "phases":[{"tempId":"phase-1","title":"...","description":"...",
              "startDate":"YYYY-MM-DD","endDate":"YYYY-MM-DD","order":1,
              "tasks":[{"tempId":"task-1","title":"...","description":"...","estimatedHours":2,
              "startDate":"YYYY-MM-DD","dueDate":"YYYY-MM-DD","criticalAssumption":"...",
              "origin":"USER_INPUT|AI_INFERRED","order":1}],
              "milestones":[{"tempId":"milestone-1","title":"...","date":"YYYY-MM-DD","order":1}]}]}
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(confirmedSnapshot, List.of());
    }

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot, List<AiPreCheckProblem> ignoredWarnings) {
        List<AiPreCheckProblem> warnings = ignoredWarnings == null ? List.of() : ignoredWarnings.stream()
                .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", confirmedSnapshot);
        context.put("explicitlyIgnoredWarnings", warnings);
        try {
            return new AiPrompt(
                    AiPromptVersions.GENERATION_PROMPT,
                    SYSTEM_INSTRUCTIONS_TEMPLATE.formatted(
                            AiSchemaVersions.GENERATION,
                            AiSchemaVersions.GENERATION),
                    objectMapper.writeValueAsString(context));
        } catch (JacksonException exception) {
            throw new GenerationException("Die bestätigten Wizard-Daten konnten nicht für die Generierung aufbereitet werden.", exception);
        }
    }
}

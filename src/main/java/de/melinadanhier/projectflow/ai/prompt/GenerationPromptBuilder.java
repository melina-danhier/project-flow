package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
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
            - Erzeuge ausschließlich Phasen mit Aufgaben und Meilensteinen.
            - Fasse den Entwurf knapp in metadata.summary zusammen und führe nur tatsächlich
              getroffene Planungsannahmen in metadata.assumptions auf.
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
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(confirmedSnapshot, List.of());
    }

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot, List<AiPreCheckProblem> ignoredWarnings) {
        return build(new AiGenerationRequest(
                confirmedSnapshot, ignoredWarnings));
    }

    public AiPrompt build(AiGenerationRequest request) {
        AiWizardSnapshot confirmedSnapshot = request.confirmedWizardData();
        List<AiPreCheckProblem> ignoredWarnings = request.explicitlyIgnoredWarnings();
        List<AiPreCheckProblem> warnings = ignoredWarnings == null ? List.of() : ignoredWarnings.stream()
                .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", confirmedSnapshot);
        context.put("explicitlyIgnoredWarnings", warnings);
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        try {
            return new AiPrompt(
                    AiPromptVersions.GENERATION_PROMPT,
                    SYSTEM_INSTRUCTIONS_TEMPLATE,
                    objectMapper.writeValueAsString(context));
        } catch (JacksonException exception) {
            throw new GenerationException("Die bestätigten Wizard-Daten konnten nicht für die Generierung aufbereitet werden.", exception);
        }
    }
}

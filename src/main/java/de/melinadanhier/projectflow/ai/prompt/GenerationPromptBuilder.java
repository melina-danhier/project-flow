package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.ai.exception.AiProviderConfigurationException;
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
            - Erzeuge insgesamt mindestens drei Aufgaben.
            - Fasse den Entwurf knapp in metadata.summary zusammen und führe nur tatsächlich
              getroffene Planungsannahmen in metadata.assumptions auf.
            - Vergib für jede Aufgabe einen im gesamten Entwurf eindeutigen, stabilen tempId-Wert.
              Er ist eine Referenz im Entwurf und keine Datenbank-ID. Phasen und Meilensteine
              benötigen keine tempId.
            - Gib für jede Aufgabe prerequisiteTaskTempIds als Liste vorhandener Aufgaben-tempId-Werte
              zurück. Nutze eine leere Liste, wenn keine Abhängigkeiten bestehen. Erzeuge weder
              Selbstabhängigkeiten noch Zyklen.
            - priority ist optional und darf nur LOW, MEDIUM oder HIGH sein. Lasse das Feld weg,
              wenn keine begründete Priorität ableitbar ist.
            - origin ist genau USER_INPUT, wenn der Inhalt unmittelbar aus einer Nutzereingabe folgt,
              andernfalls AI_INFERRED.
            - Gib keinen Prüfstatus wie checked, verified oder reviewed zurück. Neue Inhalte sind
              anwendungsseitig ungeprüft.
            - Richte die Terminierung nach dem bestätigten Zeitraum-Modus aus. Bei terminierter Planung
              benötigt jede Aufgabe dueDate und jeder Meilenstein date; Aufgaben-startDate sowie
              Phasen-startDate und -endDate bleiben optional. Ohne terminierte Planung dürfen alle
              Datumsfelder fehlen. Ergänze keine fehlenden Datumswerte durch bloße technische Annahmen.
            - Berücksichtige bestätigte Pre-Check-Warnungen als fachlichen Kontext; ändere deswegen keine Eingabe.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(confirmedSnapshot, List.of());
    }

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot, List<AiPreCheckProblem> acknowledgedWarnings) {
        return build(new AiGenerationRequest(
                confirmedSnapshot, acknowledgedWarnings));
    }

    public AiPrompt build(AiGenerationRequest request) {
        if (!AiPromptVersions.GENERATION_PROMPT.equals(request.promptVersion())) {
            throw new AiProviderConfigurationException(
                    "Die gespeicherte Generation-Prompt-Version wird serverseitig nicht unterstützt.");
        }
        AiWizardSnapshot confirmedSnapshot = request.confirmedWizardData();
        List<AiPreCheckProblem> acknowledgedWarnings = request.acknowledgedWarnings();
        List<AiPreCheckProblem> warnings = acknowledgedWarnings == null ? List.of() : acknowledgedWarnings.stream()
                .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", confirmedSnapshot);
        context.put("acknowledgedPreCheckWarnings", warnings);
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        try {
            return new AiPrompt(
                    request.promptVersion(),
                    SYSTEM_INSTRUCTIONS_TEMPLATE,
                    objectMapper.writeValueAsString(context));
        } catch (JacksonException exception) {
            throw new GenerationException("Die bestätigten Wizard-Daten konnten nicht für die Generierung aufbereitet werden.", exception);
        }
    }
}

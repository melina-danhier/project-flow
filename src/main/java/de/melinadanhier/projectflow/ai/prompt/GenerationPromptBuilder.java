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
            - origin ist genau USER_INPUT, wenn der Inhalt unmittelbar aus einer Nutzereingabe folgt,
              andernfalls AI_INFERRED.
            - Gib keinen Prüfstatus wie checked, verified oder reviewed zurück. Neue Inhalte sind
              anwendungsseitig ungeprüft.
            - Richte die Terminierung nach dem bestätigten Zeitraum-Modus aus. Bei terminierter Planung
              benötigt jede Aufgabe dueDate und jeder Meilenstein date; Aufgaben-startDate bleibt
              optional. Ohne terminierte Planung dürfen alle Datumsfelder null sein. Ergänze keine
              fehlenden Datumswerte durch bloße technische Annahmen.
            - Berücksichtige bestätigte Pre-Check-Warnungen als fachlichen Kontext; ändere deswegen keine Eingabe.
            - Gib kritische Annahmen ausschließlich global in criticalAssumptions zurück. Verknüpfe sie nicht
              mit Sections, Aufgaben oder Meilensteinen. Gib nur Annahmen aus, deren Falschheit Inhalt,
              Umfang, Aufwand, Terminplanung oder Aufbau des Plans wesentlich verändern würde. Allgemeine,
              nebensächliche oder rein beschreibende Vermutungen sind keine kritischen Annahmen.
            - Setze correctionRequiredIfRejected nur auf true, wenn die bloße Verneinung der Aussage keine
              ausreichende Information für eine korrekte Neugenerierung liefert.
            - Behandle confirmedAssumptions als verbindliche Fakten und gib sie nicht erneut als Annahmen aus.
              Behandle rejectedAssumptions als falsche Voraussetzungen und berücksichtige ausschließlich dort
              tatsächlich enthaltene Korrekturen als zusätzliche verbindliche Fakten.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(confirmedSnapshot, List.of());
    }

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot, List<AiPreCheckProblem> acknowledgedWarnings) {
        return build(new AiGenerationRequest(confirmedSnapshot, acknowledgedWarnings));
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
        context.put("acknowledgedPreCheckWarnings", request.acknowledgedWarnings());
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        context.put("confirmedAssumptions", request.confirmedAssumptions());
        context.put("rejectedAssumptions", request.rejectedAssumptions());
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

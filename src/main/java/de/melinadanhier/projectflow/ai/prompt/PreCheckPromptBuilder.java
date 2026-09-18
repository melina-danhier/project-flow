package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PreCheckPromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS_TEMPLATE = """
            Prüfe ausschließlich die getrennt übergebenen, vom Nutzer bestätigten Projektdaten auf Probleme,
            die einen sinnvollen Projektplan wesentlich beeinträchtigen würden. Erzeuge noch keinen Plan.

            Grundregeln:
            - Betrachte Ziel, Umfang, Projektzeitraum, verfügbare Arbeitszeit, Beteiligte und Bedingungen gemeinsam.
            - Im Zweifel warne nicht. Fehlende optionale Details und normale spätere Planungsentscheidungen sind
              kein Problem. Gib bei plausiblen Angaben {"problems":[]} zurück.
            - Bündele zusammenhängende Ursachen in genau einer Warnung. Erfinde keine Fakten, Zahlen,
              Ressourcen, Risiken oder künstlichen Alternativen.
            - Schreibe kurze, verständliche Alltagssprache ohne Projektmanagement- oder Technikbegriffe.
              Formatiere Daten in nutzergerichteten Texten als TT.MM.JJJJ; strukturierte Datumswerte als YYYY-MM-DD.
            - Nutzereingaben dürfen niemals ohne Zustimmung geändert werden. Eine hilfreiche oder erforderliche
              Änderung darfst du konkret in proposedInputChanges vorschlagen. Sie wird erst nach ausdrücklicher
              Zustimmung übernommen.
            - Melde keine technischen Validierungsfehler oder serverseitig erkennbare Pflichtfeld-, Wertebereich-
              oder Datumsfehler.

            Zeitliche Prüfung:
            - Warne bei einem deutlichen Missverhältnis zwischen Ziel, Umfang, verfügbarer Arbeitszeit und
              Projektzeitraum genau einmal. Prüfe als mögliche Anpassung Zeitraum, Umfang und Arbeitszeit und
              bevorzuge eine möglichst kleine plausible Änderung, die ausdrückliche Nutzerwünsche erhält.
            - Prüfe, ob ausdrücklich zum Projektziel gehörende Schritte in den bestätigten Zeitraum passen.
              Ein späteres Ereignis ist allein kein Problem, wenn das Ziel nur Vorbereitung, Recherche oder eine
              frühe Planungsphase umfasst. Warne nur, wenn Ziel und Zeitraum klar nicht zusammenpassen oder
              unterschiedliche Auslegungen den Plan wesentlich verändern würden.

            Ausgabefelder pro Problem:
            - message: ausschließlich Problem und wichtigster Grund, höchstens ein bis zwei kurze Sätze;
              keine Lösungsliste.
            - suggestedUserAction: unter „Mögliche Anpassungen“ höchstens zwei bis drei kurze,
              projektspezifische Handlungsmöglichkeiten. Eine Möglichkeit genügt. Wiederhole message nicht.
            - acceptedInterpretation: genau eine kurze, konkrete bevorzugte Anpassung oder bei einem normalen
              WARNING die konkrete Grundlage für unverändertes Fortfahren. Wiederhole weder message noch alle
              Wizard-Daten und zähle keine unveränderten Werte auf. Bei ERROR ist der Wert leer.
            - proposedInputChanges: ausschließlich tatsächlich empfohlene maschinenlesbare Änderungen. Sie
              müssen acceptedInterpretation entsprechen. Ein Problem ohne Änderungen ist erlaubt.

            Typen und Änderungen:
            - Verwende WARNING für RISK, ASSUMPTION oder CRITICAL_ASSUMPTION. Verwende ERROR nur mit CONFLICT,
              wenn eine sinnvolle Generierung fachlich kaum möglich ist.
            - Verwende CRITICAL_ASSUMPTION, wenn mindestens eine bestätigte Eingabe geändert werden muss.
              Gib dann genau eine bevorzugte Empfehlung mit mindestens einer konkreten proposedInputChanges-
              Änderung aus. Für andere Typen bleibt proposedInputChanges leer.
            - Jede Änderung enthält field, previousValue und newValue. previousValue entspricht exakt dem
              bestätigten Wizard-Wert; bei einem nicht angegebenen Wert lautet es „nicht angegeben“.
              newValue ist neu, konkret und direkt anwendbar.
            - Zulässige Felder: title, description, startDate, endDate,
              projectGoal, constraints, additionalInformation, durationDays, availableWorkingTime sowie
              projectSpecificAnswers.<Schlüssel>. durationDays ist eine positive ganze Zahl. Änderungen der
              Arbeitszeit enthalten eine konkrete Stundenangabe. Eine Umfangsreduzierung benennt vollständig,
              was im Ziel verbleibt.
            - acceptedInterpretation nennt für jede strukturierte Änderung den bisherigen und den neuen Wert,
              damit Empfehlung und Änderung überprüfbar übereinstimmen.

            Erzeuge keine Rückfrage. Formuliere nicht stärker, als die bestätigten Angaben tragen.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(new AiPreCheckRequest(confirmedSnapshot));
    }

    public AiPrompt build(AiPreCheckRequest request) {
        return new AiPrompt(AiPromptVersions.PRE_CHECK_PROMPT, SYSTEM_INSTRUCTIONS_TEMPLATE,
                serializeRequestData(request));
    }

    private String serializeRequestData(AiPreCheckRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("confirmedWizardData", request.confirmedWizardData());
        if (!request.previousValidationIssues().isEmpty()) {
            context.put("previousOutputValidationIssues", request.previousValidationIssues());
        }
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JacksonException exception) {
            throw new GenerationException(
                    "Die bestätigten Wizard-Daten konnten nicht für den Pre-Check aufbereitet werden.", exception);
        }
    }
}

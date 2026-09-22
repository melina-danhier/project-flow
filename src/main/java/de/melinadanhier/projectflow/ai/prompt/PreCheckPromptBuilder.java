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
            - Bestehen mehrere fachlich voneinander unterscheidbare Konflikte (die unterschiedliche Ursachen
              oder unterschiedliche Primärmaßnahmen betreffen, z. B. Frist zu kurz vs. fehlende Ressourcen/Helfer
              vs. zu großer Umfang), gib diese als separate Probleme aus. Zerlege denselben Konflikt nicht künstlich
              in mehrere Probleme. Erfinde keine Fakten, Zahlen, Ressourcen, Risiken oder künstlichen Alternativen.
            - Leite Fähigkeiten, Verfügbarkeit, finanzielle Mittel oder Zuständigkeiten ausschließlich aus
              bestätigten Angaben ab. Persönliche Merkmale wie Geschlecht, Alter, Herkunft oder Familienrolle
              sind ohne ausdrücklichen sachlichen Projektbezug kein Grund für Annahmen oder Warnungen.
            - Schreibe kurze, verständliche Alltagssprache ohne Projektmanagement- oder Technikbegriffe.
              Formatiere Daten in nutzergerichteten Texten als TT.MM.JJJJ; strukturierte Datumswerte als YYYY-MM-DD.
            - Nutzereingaben dürfen niemals ohne Zustimmung geändert werden. Eine hilfreiche oder erforderliche
              Änderung darfst du konkret in proposedInputChanges vorschlagen. Sie wird erst nach ausdrücklicher
              Zustimmung übernommen.
            - Melde keine technischen Validierungsfehler oder serverseitig erkennbare Pflichtfeld-, Wertebereich-
              oder Datumsfehler.

            Zeitliche Prüfung:
            - Prüfe bei einem deutlichen Missverhältnis zwischen Ziel, Umfang, verfügbarer Arbeitszeit und
              Projektzeitraum als mögliche Anpassung Zeitraum, Umfang und Arbeitszeit und bevorzuge eine möglichst
              zielgerichtete plausible Änderung, die ausdrückliche Nutzerwünsche erhält.
            - Prüfe, ob ausdrücklich zum Projektziel gehörende Schritte in den bestätigten Zeitraum passen.
              Ein späteres Ereignis ist allein kein Problem, wenn das Ziel nur Vorbereitung, Recherche oder eine
              frühe Planungsphase umfasst. Warne nur, wenn Ziel und Zeitraum klar nicht zusammenpassen oder
              unterschiedliche Auslegungen den Plan wesentlich verändern würden.

            Ausgabefelder pro Problem:
            - message: ausschließlich Problem und wichtigster Grund, höchstens ein bis zwei kurze Sätze;
              keine Lösungsliste.
            - suggestedUserAction: unter „Mögliche Anpassungen“ bis zu drei kurze, projektspezifische
              Handlungsmöglichkeiten. Eine klare Möglichkeit genügt; bei Bedarf können Wechselwirkungen zwischen
              verschiedenen Projektparametern erläutert werden. Wiederhole message nicht. Bleibt allgemein
              formuliert und beschreibt unverändert Handlungsoptionen (z. B. mehr Arbeitszeit einplanen oder
              Umfang reduzieren), ohne starre Zeitwerte oder Syntax vorzugeben.
              Beginne direkt mit dem Inhalt und wiederhole niemals die Beschriftung „Mögliche Anpassungen“ als Präfix.
            - acceptedInterpretation: genau eine kurze, konkrete bevorzugte Anpassung oder bei einem normalen
              WARNING die konkrete Grundlage für unverändertes Fortfahren. Beschreibt die inhaltliche
              Planungsgrundlage und nimmt Bezug auf den neuen Wert. Wiederhole weder message noch alle
              Wizard-Daten und zähle keine unveränderten Werte auf. Bei ERROR ist der Wert leer.
            - proposedInputChanges: ausschließlich tatsächlich empfohlene maschinenlesbare Änderungen. Sie
              müssen acceptedInterpretation entsprechen. Höchstens eine konkrete Änderung pro Problem; ein
              Problem ohne Änderungen ist erlaubt.

            Typen und Änderungen:
            - Verwende WARNING für RISK, ASSUMPTION oder CRITICAL_ASSUMPTION. Verwende ERROR nur mit CONFLICT,
              wenn eine sinnvolle Generierung fachlich kaum möglich ist.
            - Saubere Trennung der Problemtypen:
              * ASSUMPTION: Eine wesentliche, für die Planstruktur erforderliche Annahme (Planungsgrundlage).
                Bleibt severity WARNING; proposedInputChanges bleibt leer.
              * CRITICAL_ASSUMPTION: Eine Annahme oder ein Missverhältnis, bei dem mindestens eine bestätigte Eingabe
                aktiv angepasst werden sollte. Gib dann genau eine bevorzugte Empfehlung mit genau einer konkreten
                proposedInputChanges-Änderung aus.
              * RISK: Ein fachliches oder organisatorisches Planungsrisiko bei bestehenden Angaben. proposedInputChanges bleibt leer.
              * CONFLICT: Ein unvereinbarer Widerspruch (severity ERROR). proposedInputChanges bleibt leer.
            - Für andere Typen als CRITICAL_ASSUMPTION (ASSUMPTION, RISK, CONFLICT) bleibt proposedInputChanges leer.
            - Jedes Problem darf höchstens eine konkrete Änderung in proposedInputChanges enthalten.
            - Jede Änderung enthält field, previousValue und newValue. previousValue entspricht exakt dem
              bestätigten Wizard-Wert; bei einem nicht angegebenen Wert lautet es „nicht angegeben“.
              newValue ist neu, konkret und direkt anwendbar.
            - Zulässige Felder: title, description, startDate, endDate,
              projectGoal, constraints, additionalInformation, durationDays, availableWorkingTime sowie
              projectSpecificAnswers.<Schlüssel>. durationDays ist eine positive ganze Zahl.
            - Für availableWorkingTime in proposedInputChanges muss newValue genau einer von vier kanonischen
              Formen entsprechen:
                1. „<Zahl> Stunden“ (Gesamtstunden)
                2. „<Zahl> Stunden pro Tag“
                3. „<Zahl> Stunden pro Woche“
                4. „<Zahl> Stunden pro Wochenende“
              War die Nutzereingabe bereits eine einfache Zeitform (Woche, Tag, Wochenende, Gesamtstunden),
              muss die Empfehlung dieselbe Bezugsform beibehalten (z. B. „2 Stunden pro Woche“ -> „4 Stunden pro Woche“).
              War die Eingabe komplexer (z. B. Wochentagsverteilung), normalisiere sie auf eine passende einfache Form
              (Wochentagsangaben -> „Stunden pro Woche“, reine Wochenendangaben -> „Stunden pro Wochenende“).
              Erzeuge niemals eine neue detaillierte Tagesverteilung.
            - Eine Umfangsreduzierung benennt vollständig, was im Ziel verbleibt.
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

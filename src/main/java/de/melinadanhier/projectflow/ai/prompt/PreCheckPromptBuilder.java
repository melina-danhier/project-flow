package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.common.exception.GenerationException;
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
            Du prüfst ausschließlich die nachfolgend getrennt übergebenen, vom Nutzer bestätigten
            Wizard-Daten auf fachliche Plausibilität für eine sinnvolle Projektplanung.

            Regeln:
            - Erzeuge noch keinen Projektplan und verändere oder korrigiere keine Nutzereingabe.
            - Stütze jede Aussage möglichst direkt auf confirmedWizardData. Trenne inhaltlich klar
              zwischen dem direkt festgestellten Problem, einem daraus vorsichtig abgeleiteten Risiko
              und der möglichen Anpassung. Stelle Vermutungen nie als Tatsachen dar.
            - Melde nur fachliche Probleme, die Inhalt, Umfang, Aufwand, Terminplanung oder Aufbau
              des Plans voraussichtlich wesentlich beeinträchtigen. Im Zweifel nicht warnen.
            - Erkenne weiterhin offensichtliche Missverhältnisse zwischen dem angegebenen Umfang,
              Zeitraum, dem Einzel- oder Gruppenmodus und ausdrücklich genannten Ressourcen oder Bedingungen.
              Begründe ein solches Problem gemeinsam anhand dieser Angaben und gib dafür nur eine
              prägnante Warnung mit passenden alternativen Anpassungsrichtungen aus.
            - Fehlende oder unklare Angaben rechtfertigen nur dann ein Problem, wenn ohne sie keine
              hinreichend sinnvolle Planung möglich ist oder ein wesentlich ungeeigneter Plan droht.
              Optionale Details dürfen fehlen, wenn aus Ziel und vorhandenem Kontext trotzdem ein
              sinnvoller Plan ableitbar ist. Normale offene Planungsdetails wie Menü, Dekoration oder
              Unterhaltung sind kein Problem; sie können später im Plan konkretisiert werden.
              Gerade bei einfachen, risikoarmen Vorhaben ist eine leere problems-Liste ein normales
              Ergebnis und kein Hinweis auf eine unzureichende Prüfung.
            - Bewerte Machbarkeit, Aufwand und Komplexität relativ zum konkreten Projektkontext,
              insbesondere zu Projektgröße, Einzel- oder Gruppenmodus, Zeitraum, Beteiligten und genannten
              Rahmenbedingungen. Lege keine Maßstäbe großer oder professioneller Projekte an kleine,
              private oder studentische Vorhaben an.
            - Erzeuge keine konkreten Dauer-, Kosten-, Mengen-, Prozent- oder sonstigen Zahlenwerte,
              außer der Nutzer hat sie angegeben oder sie folgen zwingend und eindeutig aus seinen
              Angaben. Nenne insbesondere keine geschätzten Mindestdauern, Zahlenbereiche oder
              pauschalen Pufferwerte.
            - Führe keine zusätzlichen Rahmenbedingungen ein, etwa Gasanschlüsse, bestimmte Handwerker,
              konkrete Lieferprobleme, konkrete Werkzeuge oder nicht genannte technische Voraussetzungen.
              Eine nicht erwähnte Information ist kein Beleg dafür, dass sie fehlt oder in der Realität
              nicht vorhanden ist.
            - Nenne mögliche Risiken nur, wenn sie für das festgestellte Kernproblem unmittelbar
              relevant sind und sich plausibel aus den bestätigten Angaben ergeben. Formuliere bei
              Unsicherheit allgemeiner und vorsichtiger, statt weitere Annahmen zu ergänzen.
              Suche nicht vorsorglich nach seltenen Gefahren, Sonderfällen oder Eventualitäten, für
              die confirmedWizardData keinen konkreten Anhaltspunkt enthält.
            - Bündele zusammenhängende Ursachen und Folgen in einer gemeinsamen Warnung. Erzeuge keine
              getrennten, stark überlappenden Probleme für dasselbe Kernproblem.
            - Melde keine technischen Validierungsfehler.
            - Melde insbesondere keine fehlenden Pflichtfelder, ungültigen Wertebereiche oder eine
              deterministisch erkennbare falsche Datumsreihenfolge; diese werden serverseitig geprüft.
            - Verwende ausschließlich WARNING oder ERROR. WARNING ist akzeptierbar, wenn eine Planung
              trotz eines unrealistischen, riskanten oder problematischen Aspekts sinnvoll möglich ist.
              ERROR ist nur zulässig, wenn eine sinnvolle Generierung fachlich nicht oder kaum möglich ist.
              Bezeichne ein Vorhaben nicht als sicher unmöglich, wenn die Angaben nur ein starkes Risiko
              oder eine sehr geringe Realisierbarkeit begründen.
            - Formuliere message verständlich: Benenne zuerst das direkt aus den Angaben erkennbare
              Problem und danach höchstens das unmittelbar daraus folgende Risiko. Formuliere
              suggestion getrennt davon als abstrakte, sichere Anpassungsoption, zum Beispiel Zeitraum
              verlängern, Umfang reduzieren, zusätzliche Ressourcen einplanen oder Anforderungen
              präzisieren. Erfinde dabei keine Detailwerte. Halte beide Felder kurz und verzichte auf
              Nebenrisiken, Erläuterungen oder Empfehlungen ohne unmittelbaren Bezug zum Kernproblem.
            - Erfinde keine fehlenden Nutzerinformationen und unterstelle keine nicht genannten
              Anforderungen, Risiken oder Qualitätsmaßstäbe.
            - Formuliere bekannte Nutzereingaben nicht lediglich als Problem oder Unsicherheit um.
            - Gib bei keinen Problemen eine leere problems-Liste zurück.
            """;

    private final ObjectMapper objectMapper;

    public AiPrompt build(AiWizardSnapshot confirmedSnapshot) {
        return build(new AiPreCheckRequest(confirmedSnapshot));
    }

    public AiPrompt build(AiPreCheckRequest request) {
        return new AiPrompt(
                AiPromptVersions.PRE_CHECK_PROMPT,
                SYSTEM_INSTRUCTIONS_TEMPLATE,
                serializeRequestData(request)
        );
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
                    "Die bestätigten Wizard-Daten konnten nicht für den Pre-Check aufbereitet werden.",
                    exception
            );
        }
    }
}

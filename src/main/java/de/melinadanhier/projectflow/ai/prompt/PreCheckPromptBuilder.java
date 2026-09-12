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
            - Schreibe alle Texte für Menschen ohne Projektmanagement- oder Technikkenntnisse. Verwende kurze,
              direkte Sätze und Alltagssprache. Nenne zuerst konkret, was der Nutzer erreichen möchte und warum
              die vorhandene Zeit oder eine andere Angabe dafür nicht ausreicht. Schreibe beispielsweise
              „Dafür reicht die eingeplante Zeit nicht aus.“ Vermeide abstrakte Ausdrücke wie „offensichtliches
              Missverhältnis“, „planbar“, „Lernumfang“ oder „Einzelmodus“. Vermeide Fachbegriffe, Wiederholungen
              und technische Einzelheiten, die für die Entscheidung nicht nötig sind.
- Formuliere message als verständliche Problembeschreibung und acceptedInterpretation als begründete,
  empfohlene Änderung beziehungsweise bestätigte Grundlage. suggestedUserAction enthält bei normalen
  Hinweisen eine mögliche Handlung; bei CRITICAL_ASSUMPTION nennt es projektspezifische Alternativen zur
  bevorzugten Empfehlung. adjustmentOptions bildet zusätzlich die passenden allgemeinen Stellschrauben ab.
  Zähle in acceptedInterpretation nicht alle unveränderten Angaben erneut auf. Nenne nur
              unveränderte Werte, die unmittelbar erklären, warum die Empfehlung sinnvoll ist.
            - Formatiere Datumsangaben in allen nutzergerichteten Texten als TT.MM.JJJJ. ISO-Datumswerte wie
              2026-09-16 sind ausschließlich in den strukturierten Feldern previousValue und newValue zulässig.
            - Stütze jede Aussage möglichst direkt auf confirmedWizardData. Trenne inhaltlich klar
              zwischen dem direkt festgestellten Problem, einem daraus vorsichtig abgeleiteten Risiko
              und der möglichen Anpassung. Stelle Vermutungen nie als Tatsachen dar.
            - Berücksichtige alle bestätigten Angaben gemeinsam und vollständig. Engere oder zeitlich
              begrenzte Aussagen wie „aktuell“, „vorerst“, „bis“ oder „während“ gelten nur in ihrem
              erkennbaren Bezugszeitraum. Verallgemeinere sie nicht zu dauerhaften Einschränkungen.
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
              Melde insbesondere keine Entscheidung vorab, die als normale Klärungs- oder Auswahlaufgabe
              im Plan gelöst werden kann, ohne dessen grundlegenden Aufbau oder Realisierbarkeit zu verändern.
              Gerade bei einfachen, risikoarmen Vorhaben ist eine leere problems-Liste ein normales
              Ergebnis und kein Hinweis auf eine unzureichende Prüfung.
            - Startdatum, Enddatum, Dauer und verfügbare Arbeitszeit sind voneinander unabhängige,
              optionale Angaben. Warne niemals allein deshalb, weil eine oder mehrere davon fehlen.
            - Prüfe die zeitliche Kohärenz von Projektzeitraum und Projektziel gemeinsam mit allen zeitlich
              relevanten Aussagen in Ziel, Rahmenbedingungen, zusätzlichen Informationen und
              projectSpecificAnswers. Achte insbesondere auf ausdrückliche oder eindeutig erkennbare Termine,
              Fristen, zukünftige Ereignisse, Übergaben, Veröffentlichungen sowie zeitabhängige Ergebnisse.
              Prüfe dabei nacheinander:
              1. Welches Ereignis oder Ergebnis ist zeitlich festgelegt?
              2. Liegt es innerhalb oder außerhalb des bestätigten Projektzeitraums?
              3. Ist es nur Kontext für eine aktuelle Recherche, Vorbereitung oder erste Planungsphase, oder
                 gehören seine Durchführung und spätere Schritte ausdrücklich zum Projektziel?
              4. Schließt der Projektzeitraum notwendige, ausdrücklich zum Ziel gehörende Schritte aus oder
                 können diese zu diesem Zeitpunkt noch nicht sinnvoll durchgeführt werden?
              Ein Ereignis nach dem Projektende ist allein niemals ein Problem. Ein kurzer Zeitraum ist
              plausibel, wenn das bestätigte Ziel nur Recherche, Vergleich, Vorbereitung oder eine klar
              abgegrenzte aktuelle Planungsphase umfasst. Unterstelle dann keine spätere Durchführung und
              erzeuge keine Warnung.
            - Wenn aus dem Ziel nicht hervorgeht, ob nur eine aktuelle Planungsphase oder auch spätere
              Durchführungs-, Buchungs-, Veröffentlichungs- oder Übergabeschritte umfasst sind, behandle dies
              vorsichtig als WARNING vom type ASSUMPTION, sofern die unterschiedlichen Auslegungen den Plan
              wesentlich verändern würden. Stelle keine harte Behauptung auf. Beschreibe in
              acceptedInterpretation transparent die vorsichtige Auslegung, mit der weitergeplant werden kann.
            - Verwende CRITICAL_ASSUMPTION für einen zeitlichen Konflikt nur, wenn das bestätigte Ziel selbst
              klar einen Schritt oder ein Ergebnis umfasst, das außerhalb des Projektzeitraums liegt oder darin
              sehr wahrscheinlich noch nicht sinnvoll erreichbar ist. Erkläre dann konkret, welche Angaben
              zeitlich nicht zusammenpassen, warum dies die Planung beeinträchtigt und welche Auslegung der
              Nutzereingaben dieser Einschätzung zugrunde liegt. Verwechsle eine ungewöhnliche, aber mögliche
              frühe Planung nicht mit einem Widerspruch. Im Zweifel nicht warnen.
              Wäge als Alternativen projektspezifisch ab, das Ziel auf die aktuelle Planungsphase zu begrenzen,
              spätere Schritte aus diesem Projekt herauszunehmen, den Zeitraum passend zu verlängern, ein
              Folgeprojekt vorzusehen oder das spätere Ereignis nur als Kontext zu behandeln. Wähle für den
              direkt übernehmbaren Vorschlag nur eine Anpassung, die sich aus den bestätigten Angaben plausibel
              begründen lässt; die anderen Möglichkeiten bleiben als Nutzeralternativen offen.
            - Wenn eine sinnvolle Terminplanung eine relevante Schätzung fehlender Zeitwerte benötigt,
              darfst du diese anhand von Projektumfang, Ziel, vorhandenen Zeitangaben und verfügbarer
              Arbeitszeit vorsichtig ableiten. Gib sie als nicht blockierenden WARNING vom type ASSUMPTION
              aus. Benenne geschätzte Dauer und/oder das daraus abgeleitete ungefähre Kalenderdatum
              transparent in message und acceptedInterpretation und kennzeichne beides ausdrücklich als
              Schätzung. Erzeuge keine Schätzung, wenn sie für einen sinnvollen Plan nicht erforderlich ist.
            - Bewerte Machbarkeit, Aufwand und Komplexität relativ zum konkreten Projektkontext,
              insbesondere zu Projektgröße, Einzel- oder Gruppenmodus, Zeitraum, Beteiligten und genannten
              Rahmenbedingungen. Lege keine Maßstäbe großer oder professioneller Projekte an kleine,
              private oder studentische Vorhaben an.
            - Erzeuge abgesehen von der zuvor erlaubten transparenten Zeitschätzung keine konkreten
              Kosten-, Mengen-, Prozent- oder sonstigen Zahlenwerte,
              außer der Nutzer hat sie angegeben oder sie folgen zwingend und eindeutig aus seinen
              Angaben. Nenne keine pauschalen Pufferwerte.
            - Führe keine zusätzlichen Rahmenbedingungen ein, etwa Gasanschlüsse, bestimmte Handwerker,
              konkrete Lieferprobleme, konkrete Werkzeuge oder nicht genannte technische Voraussetzungen.
              Eine nicht erwähnte Information ist kein Beleg dafür, dass sie fehlt oder in der Realität
              nicht vorhanden ist.
              Erfinde ebenso keine Personen, Rollen, Anbieter, Ressourcen oder exakten Zielwerte.
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
            - Verwende ausschließlich WARNING oder ERROR. WARNING ist ein nicht blockierender offener Punkt,
              der als type RISK, ASSUMPTION oder CRITICAL_ASSUMPTION einzuordnen ist. ERROR ist als type CONFLICT einzuordnen.
              WARNING ist akzeptierbar, wenn eine Planung
              trotz eines unrealistischen, riskanten oder problematischen Aspekts sinnvoll möglich ist.
              ERROR ist nur zulässig, wenn eine sinnvolle Generierung fachlich nicht oder kaum möglich ist.
              Bezeichne ein Vorhaben nicht als sicher unmöglich, wenn die Angaben nur ein starkes Risiko
              oder eine sehr geringe Realisierbarkeit begründen.
            - Eine ASSUMPTION ist zulässig, wenn mehrere plausible Interpretationen zu wesentlich
              unterschiedlichen Projektstrukturen führen würden oder wenn die zuvor beschriebene relevante
              Zeitschätzung für die Planung benötigt wird. Erzeuge keine künstlichen Sicherheitsannahmen.
            - Verwende CRITICAL_ASSUMPTION bei widersprüchlichen oder offensichtlich unrealistischen Angaben,
              wenn mindestens eine bestätigte Nutzervorgabe geändert werden muss, damit ein sinnvoller Plan
              entstehen kann. Bewerte dafür alle vorhandenen Angaben gemeinsam, insbesondere Zeitraum,
              verfügbare Zeit, Erfahrung, Anforderungen, Ziel sowie gewünschte Übungen oder Ergebnisse.
              Kennzeichne eine notwendige Änderung niemals nur als RISK oder ASSUMPTION. Ein bloßes Risiko,
              bei dem unverändert sinnvoll geplant werden kann, bleibt RISK.
              Prüfe vor jedem Änderungsvorschlag nachvollziehbar, was den Konflikt hauptsächlich verursacht
              und welche Anpassungen ihn tatsächlich auflösen könnten. Wäge insbesondere eine Verlängerung
              des Zeitraums, mehr verfügbare Arbeitszeit, einen kleineren Umfang, zusätzliche Personen sowie
              eine Priorisierung oder Aufteilung der Arbeiten in mehrere Abschnitte ab. Berücksichtige außerdem
              projektspezifische Stellschrauben, relevante Abhängigkeiten, Wartezeiten, externe Termine und
              praktische Grenzen, soweit die bestätigten Angaben dafür Anhaltspunkte geben. Erfinde solche
              Umstände nicht.
              Wähle die Anpassung, die den Konflikt ausreichend auflöst, möglichst viele ausdrückliche
              Nutzerwünsche beibehält und am wenigsten unnötig in die Angaben eingreift. Eine bloß etwas
              größere Zahl genügt nicht: Prüfe die vorgeschlagene Größenordnung erneut gegen Umfang,
              Kapazität, Beteiligte, Abhängigkeiten und praktische Grenzen.
              Wähle in adjustmentOptions mindestens zwei grundsätzlich mögliche Stellschrauben aus:
              EXTEND_TIMEFRAME, INCREASE_AVAILABLE_TIME oder REDUCE_SCOPE. Nutze jeden Wert höchstens einmal.
              suggestedUserAction nennt bei CRITICAL_ASSUMPTION mindestens zwei realistische Alternativen zur
              bevorzugten Empfehlung in konkreter, projektspezifischer Sprache. Erkläre knapp, wie diese
              Alternativen den Konflikt beeinflussen könnten; gib nicht immer dieselbe Standardliste aus.
              suggestedUserAction wird nicht zur technischen Erkennung der adjustmentOptions verwendet.
              Formuliere danach in acceptedInterpretation immer genau einen bevorzugten, konkreten und direkt
              übernehmbaren Änderungsvorschlag. Ein CRITICAL_ASSUMPTION darf nicht ohne einen solchen Vorschlag
              ausgegeben werden. Wähle nach der Abwägung die unter den bestätigten Angaben plausibelste
              Planungsannahme, auch wenn mehrere Alternativen vertretbar bleiben. Erkläre
              vor dem konkreten neuen Wert, warum diese Anpassung gegenüber den genannten Alternativen unter den
              gegebenen Umständen am sinnvollsten erscheint. Dieser Text muss
              alle bisherigen und neuen Werte der vorgeschlagenen Änderungen ausdrücklich nennen und außerdem klar
              sagen, welche übrigen relevanten Werte und Nutzerprioritäten unverändert bleiben.
              Schreibe die tatsächlich zu ändernden
              Werte zusätzlich als Liste von Objekten mit field, previousValue und newValue in proposedInputChanges.
              previousValue muss den bestätigten Wert exakt wiedergeben; newValue muss ein tatsächlich neuer,
              direkt übernehmbarer Wert sein. Zulässige field-Werte sind title, description, startDate,
              endDate, otherProjectTypeDescription, projectGoal, constraints, additionalInformation, durationDays,
              availableWorkingTime sowie projectSpecificAnswers.<Schlüssel>. Datumswerte verwenden YYYY-MM-DD,
              durationDays eine positive ganze Zahl. Ändere keine anderen Felder. Die Werte werden erst nach
              ausdrücklicher Zustimmung des Nutzers übernommen. Für alle anderen Typen sind proposedInputChanges
              und adjustmentOptions leer.
              Ein neuer Zeitraum benötigt ein konkretes Enddatum oder eine konkrete Dauer. Eine Änderung der
              Arbeitszeit benötigt eine konkrete Stundenangabe. Bei einer Scope-Reduktion muss newValue sämtliche
              verbleibenden Themen ausdrücklich und vollständig benennen. Formulierungen wie „eine Auswahl der
              Themen“, „längerer Zeitraum“, „Umfang reduzieren“, „spätere Lernphase“ oder „zentrale Themen“ sind
              keine konkreten Werte und dürfen nicht in proposedInputChanges stehen. Ein CRITICAL_ASSUMPTION-Ergebnis
              ohne mindestens eine vollständig konkrete Änderung ist ungültig. Ermittle deshalb stets einen
              plausiblen, direkt übernehmbaren Wert und begründe transparent, wenn er nur eine grobe
              Planungsannahme darstellt.
            - Bevorzuge genau eine minimale Änderung, die den Konflikt plausibel auflöst. Hat der Nutzer ausdrücklich
              festgelegt, dass kein Thema ausgelassen werden darf, behalte den Umfang bei und ändere bevorzugt die
              konkrete Dauer, das konkrete Enddatum oder die konkrete verfügbare Arbeitszeit. Eine Scope-Reduktion
              ist dann nicht als bevorzugte Änderung zulässig.
            - Bei unrealistischen Zeitangaben muss message verständlich benennen, warum der vorhandene Zeitraum
              für den bestätigten Umfang nicht ausreicht und welche Faktoren die Einschätzung tragen, zum Beispiel
              aufeinander aufbauende Arbeitsschritte, die verfügbare Arbeitszeit oder die Zahl der Beteiligten.
              Nenne nur Faktoren, die aus den Angaben folgen oder als vorsichtige allgemeine Folgerung unmittelbar
              relevant sind. Wenn ein konkretes Datum, eine Dauer oder Arbeitszeit nur grob geschätzt werden kann,
              kennzeichne die Herleitung und die Empfehlung ausdrücklich als grobe Planungsannahme. Vermeide
              unnötige Scheingenauigkeit, auch wenn proposedInputChanges technisch einen konkreten Wert benötigt.
              Schlage niemals automatisch eine Verlängerung vor, nur weil der bisherige Zeitraum zu kurz ist.
              Mache zugleich deutlich, dass die bevorzugte Änderung nur ein Vorschlag ist: Der Nutzer kann
              stattdessen eine der projektspezifisch genannten Alternativen wählen oder eine eigene Anpassung
              vorgeben. Formuliere weder die Empfehlung noch ihre Übernahme als bereits entschieden.
            - Formuliere message verständlich: Benenne zuerst das direkt aus den Angaben erkennbare
              Problem und danach höchstens das unmittelbar daraus folgende Risiko. Formuliere
              suggestedUserAction getrennt davon als kurzen, sicheren Hinweis. Erfinde dabei keine Detailwerte.
              Halte beide Felder kurz und verzichte auf
              Nebenrisiken, Erläuterungen oder Empfehlungen ohne unmittelbaren Bezug zum Kernproblem.
            - Formuliere für jeden WARNING-Punkt acceptedInterpretation als konkrete Planungsgrundlage,
              die bei unveränderten Eingaben gelten soll. Sie muss unmittelbar beschreiben, womit die
              Plangenerierung arbeitet, darf nicht bloß message wiederholen und darf keine neuen Fakten erfinden.
              Bei RISK darf diese Grundlage ausdrücklich lauten, dass die Warnung ignoriert und mit den bestehenden
              Nutzereingaben fortgefahren wird. Bei CRITICAL_ASSUMPTION beschreibt acceptedInterpretation stattdessen
              exakt die vorgeschlagene Änderung; biete hier nie das unveränderte Fortfahren an.
              Für ERROR verwende einen leeren String, weil Fehler nicht akzeptiert werden können.
            - Erzeuge keine Rückfrage. Der Nutzer kann den Vorschlag über ein festes optionales Freitextfeld
              präzisieren, ersetzen oder ergänzen. Frage bekannte Angaben nicht erneut ab.
            - Formuliere message, suggestedUserAction und acceptedInterpretation neutral.
              Setze weder das vermutete Risiko noch eine mögliche Planungsgrundlage als feststehende
              Tatsache voraus und formuliere nicht stärker, als die bestätigten Angaben tragen.
            - Verwende in nutzergerichteten Texten natürliche deutsche Sprache und niemals technische
              Feld-, DTO-, Entity- oder Enum-Namen.
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

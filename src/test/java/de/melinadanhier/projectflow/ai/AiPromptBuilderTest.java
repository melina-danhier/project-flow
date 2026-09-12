package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBuilderTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PreCheckPromptBuilder preCheckPromptBuilder = new PreCheckPromptBuilder(objectMapper);
    private final GenerationPromptBuilder generationPromptBuilder = new GenerationPromptBuilder(objectMapper);

    @Test
    void preCheckPromptSeparatesRulesAndConfirmedDataAndPinsVersions() {
        var prompt = preCheckPromptBuilder.build(snapshot());

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.PRE_CHECK_PROMPT);
        assertThat(prompt.systemInstructions())
                .isNotBlank()
                .doesNotContain("Umzug planen");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(snapshot()));
        assertThat(objectMapper.readTree(prompt.confirmedUserData())
                .at("/confirmedWizardData/subcategory").asText()).isEqualTo("MOVING");
        assertThat(prompt.confirmedUserData()).doesNotContain("\"projectType\"");
    }

    @Test
    void preCheckPromptWarnsOnlyAboutMaterialContextualPlanningProblems() {
        var instructions = preCheckPromptBuilder.build(snapshot()).systemInstructions();

        assertThat(instructions)
                .contains("voraussichtlich wesentlich beeinträchtigen")
                .contains("Im Zweifel nicht warnen")
                .contains("Menü, Dekoration oder", "Unterhaltung sind kein Problem")
                .contains("Projektgröße, Einzel- oder Gruppenmodus, Zeitraum")
                .contains("zeitlich", "begrenzte Aussagen", "nicht zu dauerhaften Einschränkungen")
                .contains("normale Klärungs- oder Auswahlaufgabe")
                .contains("Personen, Rollen, Anbieter, Ressourcen")
                .contains("Erzeuge keine Rückfrage")
                .doesNotContain("reviewQuestion")
                .contains("mindestens zwei grundsätzlich mögliche Stellschrauben")
                .contains("immer genau einen bevorzugten, konkreten")
                .contains("zusätzliche Personen", "Aufteilung der Arbeiten in mehrere Abschnitte")
                .contains("am wenigsten unnötig in die Angaben eingreift")
                .contains("Eine bloß etwas", "größere Zahl genügt nicht")
                .contains("mindestens zwei realistische Alternativen")
                .contains("nicht immer dieselbe Standardliste")
                .contains("warum diese Anpassung gegenüber den genannten Alternativen")
                .contains("immer genau einen bevorzugten, konkreten und direkt")
                .contains("darf nicht ohne einen solchen Vorschlag")
                .contains("nur ein Vorschlag ist")
                .contains("eine eigene Anpassung")
                .contains("grobe Planungsannahme")
                .contains("niemals automatisch eine Verlängerung")
                .contains("previousValue und newValue")
                .contains("Menschen ohne Projektmanagement- oder Technikkenntnisse")
                .contains("Dafür reicht die eingeplante Zeit nicht aus")
                .contains("TT.MM.JJJJ")
                .contains("Warnung ignoriert und mit den bestehenden")
                .contains("CRITICAL_ASSUMPTION")
                .contains("mindestens eine bestätigte Nutzervorgabe geändert werden muss")
                .contains("niemals nur als RISK oder ASSUMPTION")
                .contains("Zeitraum", "verfügbare Zeit", "Erfahrung", "Anforderungen", "gewünschte Übungen oder Ergebnisse")
                .contains("proposedInputChanges")
                .contains("erst nach", "ausdrücklicher Zustimmung")
                .contains("formuliere nicht stärker")
                .contains("unterstelle keine nicht genannten");
    }

    @Test
    void generationPromptAlignsEffortAndSchedulingWithDailyCapacity() {
        String instructions = generationPromptBuilder.build(snapshot()).systemInstructions();

        assertThat(instructions)
                .contains("Aufwand für jede Aufgabe")
                .contains("Gesamtaufwand des Plans")
                .contains("Tageskapazität")
                .contains("mehr geschätzte Aufgabenstunden", "als verfügbar");
        assertThat(instructions).doesNotContain("USER_INPUT", "AI_INFERRED", "origin ist");
    }

    @Test
    void preCheckPromptAcceptsSimpleIncompleteButPlannableProjectsWithoutInventedWarnings() {
        var prompt = preCheckPromptBuilder.build(simplePrivateProject());

        assertThat(prompt.systemInstructions())
                .contains("Optionale Details dürfen fehlen")
                .contains("eine leere problems-Liste ein normales", "Ergebnis")
                .contains("seltenen Gefahren, Sonderfällen oder Eventualitäten")
                .contains("keinen konkreten Anhaltspunkt")
                .contains("bekannte Nutzereingaben nicht lediglich als Problem");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(simplePrivateProject()));
    }

    @Test
    void preCheckPromptKeepsRenovationRiskGroundedWithoutInventingPreciseDetails() {
        var renovation = new AiWizardSnapshot(
                "Wohnung renovieren",
                "Eine 80-m²-Wohnung vollständig renovieren",
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 6),
                CollaborationMode.INDIVIDUAL,
                ProjectCategory.HOME,
                ProjectSubCategory.RENOVATION_OR_HOME_PROJECT,
                null,
                "Die vollständige Renovierung an einem Wochenende abschließen",
                "Eine Person arbeitet allein",
                null);

        var prompt = preCheckPromptBuilder.build(renovation);
        var instructions = prompt.systemInstructions();

        assertThat(instructions)
                .contains("offensichtliche Missverhältnisse")
                .contains("transparenten Zeitschätzung")
                .contains("keine pauschalen Pufferwerte")
                .contains("Eine nicht erwähnte Information ist kein Beleg")
                .contains("Gasanschlüsse, bestimmte Handwerker")
                .contains("für das festgestellte Kernproblem")
                .contains("relevant sind")
                .contains("Bündele zusammenhängende Ursachen und Folgen")
                .contains("nur eine", "prägnante Warnung")
                .contains("nicht als sicher unmöglich")
                .contains("adjustmentOptions", "EXTEND_TIMEFRAME", "INCREASE_AVAILABLE_TIME", "REDUCE_SCOPE")
                .contains("Halte beide Felder kurz")
                .contains("verzichte auf", "Empfehlungen ohne unmittelbaren Bezug")
                .contains("direkt festgestellten Problem")
                .doesNotContain("80-m²");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(renovation));
        assertThat(prompt.confirmedUserData())
                .doesNotContain("Gasanschluss", "Fremdhandwerker", "Lieferproblem");
    }

    @Test
    void generationPromptSeparatesRulesFromConfirmedDataAndUsesAcceptedInterpretations() {
        var warning = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, "Zeitraum knapp", "Mehr Zeit einplanen");
        var otherWarning = new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, "Budget knapp", "Umfang reduzieren");
        var warnings = List.of(warning, otherWarning);

        var prompt = generationPromptBuilder.build(snapshot(), warnings);

        assertThat(prompt.version()).isEqualTo(AiPromptVersions.GENERATION_PROMPT);
        assertThat(prompt.systemInstructions())
                .isNotBlank()
                .doesNotContain("Umzug planen", "Zeitraum knapp");
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedWizardData"))
                .isEqualTo(objectMapper.valueToTree(snapshot()));
        assertThat(objectMapper.readTree(prompt.confirmedUserData()).get("confirmedPlanningContext"))
                .isEqualTo(objectMapper.valueToTree(List.of(
                        "Mehr Zeit einplanen", "Umfang reduzieren")));
    }

    @Test
    void generationPromptRequiresContextFitMilestonesCompletenessAndNoInventedDetails() {
        var instructions = generationPromptBuilder.build(snapshot()).systemInstructions();

        assertThat(instructions)
                .contains("Detailtiefe, Aufgabenumfang und Komplexität")
                .contains("wichtige erreichte Zustände, Ergebnisse oder Ereignisse")
                .contains("nicht als auszuführende Tätigkeit")
                .contains("gesamten Entwurf auf diese inhaltliche Vollständigkeit")
                .contains("Aufgabe zur Klärung bzw. Entscheidung")
                .contains("jedes ausdrücklich bestätigte Endergebnis")
                .contains("Zuständigkeiten verteilen")
                .contains("Meilenstein logisch", "zeitlich erst nach")
                .contains("Bei einer Planung mit festgelegtem Start- und Enddatum muss jeder erzeugte Meilenstein")
                .contains("konkretes Datum innerhalb des Projektzeitraums besitzen")
                .contains("Das Datum muss nach allen Aufgaben liegen")
                .contains("durch die der Meilenstein erreicht wird")
                .contains("Wenn kein sinnvoller Meilenstein mit einem plausiblen")
                .contains("Datum bestimmt werden kann, soll kein Meilenstein erzeugt werden")
                .contains("letzten bestätigten", "Projekttag", "Tag dieses Ereignisses")
                .contains("verlängere", "niemals stillschweigend")
                .contains("Behaupte kein erfundenes Ergebnis");
    }

    @Test
    void generationPromptKeepsSimpleProjectsCompactScopedAndHonestAboutEffort() {
        var prompt = generationPromptBuilder.build(simplePrivateProject());

        assertThat(prompt.systemInstructions())
                .contains("eine bis drei Sections")
                .contains("ungefähr fünf bis zehn substanzielle Aufgaben")
                .contains("Orientierungswert", "kein Mindestumfang")
                .contains("eng beim ausdrücklich bestätigten Projektziel")
                .contains("Ein lediglich denkbarer oder üblicher Weg ist nicht automatisch erforderlich")
                .contains("Bewahre sprachliche Einschränkungen", "nicht zu dauerhaften Voraussetzungen")
                .contains("estimatedHours ist optional")
                .contains("Setze den Wert auf null")
                .contains("ohne", "scheinbare Präzision")
                .contains("bestätigten Planungsgrundlagen");
    }

    @Test
    void generationPromptContainsBindingAcceptedInterpretations() {
        var request = new AiGenerationRequest(snapshot(), List.of(new AiPreCheckProblem(
                AiPreCheckSeverity.WARNING, AiPreCheckProblemType.ASSUMPTION,
                "Die verfügbare Zeit ist unklar.", "Ergänze deine verfügbare Zeit.",
                "Plane mit vier Stunden pro Woche.")), List.of());

        var prompt = generationPromptBuilder.build(request);

        assertThat(prompt.systemInstructions())
                .contains("verbindlich", "hinterfrage oder interpretiere", "nicht erneut")
                .contains("confirmedWizardData, danach", "confirmedPlanningContext")
                .contains("Relative Werte", "Woche 1", "Datumsfeldern")
                .contains("Nutzereingaben", "niemals ersetzen");
        var data = objectMapper.readTree(prompt.confirmedUserData());
        assertThat(data.at("/confirmedPlanningContext/0").asText())
                .isEqualTo("Plane mit vier Stunden pro Woche.");
    }

    @Test
    void preCheckPromptAllowsTransparentTimeEstimatesWithoutWarningForMissingFieldsAlone() {
        var partial = new AiWizardSnapshot(
                "Java-Projekt", "Eine private Java-Anwendung entwickeln",
                LocalDate.of(2026, 9, 15), null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.SOFTWARE_TECHNOLOGY,
                ProjectSubCategory.SOFTWARE_PROJECT, null, null, null,
                "Zuerst ein nutzbares MVP", null, "Etwa 2 Stunden täglich", java.util.Map.of());

        var prompt = preCheckPromptBuilder.build(partial);

        assertThat(prompt.systemInstructions())
                .contains("Warne niemals allein deshalb")
                .contains("WARNING vom type ASSUMPTION")
                .contains("Schätzung");
        assertThat(prompt.confirmedUserData())
                .contains("availableWorkingTime", "Etwa 2 Stunden täglich")
                .contains("additionalInformation", "Zuerst ein nutzbares MVP");
    }

    @Test
    void preCheckPromptDistinguishesFutureContextFromTemporalGoalConflicts() {
        String instructions = preCheckPromptBuilder.build(snapshot()).systemInstructions();

        assertThat(instructions)
                .contains("zeitliche Kohärenz")
                .contains("Ziel, Rahmenbedingungen, zusätzlichen Informationen")
                .contains("Ist es nur Kontext für eine aktuelle Recherche")
                .contains("Ein Ereignis nach dem Projektende ist allein niemals ein Problem")
                .contains("klar", "abgegrenzte aktuelle Planungsphase")
                .contains("WARNING vom type ASSUMPTION")
                .contains("Stelle keine harte Behauptung auf")
                .contains("klar einen Schritt oder ein Ergebnis umfasst")
                .contains("ungewöhnliche, aber mögliche", "frühe Planung")
                .contains("das Ziel auf die aktuelle Planungsphase zu begrenzen")
                .contains("Folgeprojekt vorzusehen")
                .contains("Nutzeralternativen offen");
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.GROUP, ProjectCategory.HOME, ProjectSubCategory.MOVING, null,
                "Bis Monatsende umziehen", "Budget 2.000 Euro", "Kartons sind vorhanden");
    }

    private AiWizardSnapshot simplePrivateProject() {
        return new AiWizardSnapshot(
                "Keller ausmisten",
                "Nicht mehr benötigte Gegenstände aussortieren und Keller übersichtlich neu ordnen",
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 25),
                CollaborationMode.INDIVIDUAL, ProjectCategory.HOME,
                ProjectSubCategory.RENOVATION_OR_HOME_PROJECT, null,
                "Keller ausmisten und neu ordnen", null, null);
    }
}

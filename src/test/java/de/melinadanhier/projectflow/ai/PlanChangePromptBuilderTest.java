package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import de.melinadanhier.projectflow.ai.prompt.PlanChangePromptBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;

class PlanChangePromptBuilderTest {
    @Test
    void instructsModelToResolveMisspelledAndSemanticSectionHintsToRealReferences() {
        var prompt = new PlanChangePromptBuilder(new ObjectMapper()).build(new AiPlanChangeRequest(
                "Ergänze in Transprot eine Aufgabe.",
                new AiImprovementProjectContext("Umzug", null, null, null),
                new AiImprovementPlanContext(List.of())));

        assertThat(prompt.systemInstructions())
                .contains("Rechtschreibfehler")
                .contains("in den passenden Bereich")
                .contains("fachlich passendste vorhandene Section")
                .contains("reference als targetSectionId")
                .contains("niemals den Section-Titel");
        assertThat(prompt.systemInstructions())
                .contains("applicability=NOT_APPLICABLE")
                .contains("Konstruiere keine nur")
                .contains("Dehnroutine")
                .contains("Aufsatz über Klimawandel")
                .contains("Bei Unsicherheit lehne eher ab");
    }

    @Test
    void schemaRestrictsChangedFieldsInsteadOfAcceptingArbitraryStrings() {
        assertThat(AiResponseSchemas.forType(AiPlanChangeResponse.class).toString())
                .contains("estimatedHours", "changedFields", "enum")
                .doesNotContain("changedFields={type=string}");
    }

    @Test
    void instructsModelAboutExclusivePlacementMultipleSectionsAndApplicabilityCases() {
        var prompt = new PlanChangePromptBuilder(new ObjectMapper()).build(new AiPlanChangeRequest(
                "Ergänze je eine Aufgabe in beiden Bereichen.",
                new AiImprovementProjectContext("Umzug", null, null, null),
                new AiImprovementPlanContext(List.of())));

        assertThat(prompt.systemInstructions())
                .contains("exakt eine dieser beiden Referenzen")
                .contains("start, end, first, last")
                .contains("placement auf")
                .contains("Mehrere Änderungen, mehrere neue Elemente")
                .contains("Änderungen über mehrere Sections")
                .contains("mindestens sections, tasks oder milestones")
                .contains("summary oder explanation niemals als rejectionReason")
                .contains("NOT_APPLICABLE: sections, tasks und milestones sind leer");
    }

    @Test
    void limitsFreeTextRequestsToSupportedOperationsWithoutReducingMultiElementChanges() {
        var prompt = new PlanChangePromptBuilder(new ObjectMapper()).build(new AiPlanChangeRequest(
                "Verschiebe eine Aufgabe und plane ihre Termine neu.",
                new AiImprovementProjectContext("Umzug", null, null, null),
                new AiImprovementPlanContext(List.of())));

        assertThat(prompt.systemInstructions())
                .contains("ausschließlich als Kombination der unterstützten Operationen")
                .contains("ADD, MODIFY, MOVE und REPLAN")
                .contains("Erfinde keine weiteren Operationen")
                .contains("bestehende Elemente löschen")
                .contains("Dependencies oder Assignees automatisch ändern")
                .contains("Completion-State ändern")
                .contains("Deute solche Wünsche nicht kreativ")
                .contains("Lösche alle bisherigen Aufgaben")
                .contains("Ersetze den gesamten Plan")
                .contains("Ändere automatisch alle Abhängigkeiten")
                .contains("mehrere neue Elemente")
                .contains("mehrere Sections");
    }
}

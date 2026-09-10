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
}

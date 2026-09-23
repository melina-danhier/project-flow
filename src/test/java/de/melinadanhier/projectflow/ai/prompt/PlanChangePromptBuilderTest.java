package de.melinadanhier.projectflow.ai.prompt;

import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementPlanContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanChangePromptBuilderTest {

    @Test
    void instructsModelToLeaveUngroundedDatesEmpty() {
        var request = new AiPlanChangeRequest("Ergänze eine Aufgabe.",
                new AiImprovementProjectContext("Projekt", null, null, null),
                new AiImprovementPlanContext(List.of()));

        AiPrompt prompt = new PlanChangePromptBuilder(new ObjectMapper()).build(request);

        assertThat(prompt.version()).isEqualTo("plan-change-v5");
        assertThat(prompt.systemInstructions())
                .contains("Erfinde oder schätze kein Datum")
                .contains("Plan insgesamt nicht datumsabhängig")
                .contains("Im Zweifel null statt eines erfundenen Datums");
    }
}

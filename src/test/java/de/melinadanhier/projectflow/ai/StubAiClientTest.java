package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiGenerationScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiPreCheckScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiProperties;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubAiClientTest {

    private final StubAiProperties properties = new StubAiProperties();
    private final StubAiClient client = new StubAiClient(properties);

    @Test
    void normalScenariosAreDeterministicAndPassSharedValidation() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var generationValidator = new de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator(factory.getValidator());
            var preCheckValidator = new de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator(factory.getValidator());
            for (var scenario : StubAiPreCheckScenario.values()) {
                properties.setPreCheckScenario(scenario);
                var result = client.preCheck(preCheckRequest());
                assertThat(client.preCheck(preCheckRequest())).isEqualTo(result);
                preCheckValidator.validate(result);
            }
            var datedRequest = generationRequest();
            assertThat(generationValidator.validate(client.generatePlan(datedRequest), datedRequest).isValid()).isTrue();
            assertThat(client.generatePlan(datedRequest)).isEqualTo(client.generatePlan(datedRequest));
            properties.setGenerationScenario(StubAiGenerationScenario.WITHOUT_DATES);
            var noDates = new AiGenerationRequest(new AiWizardSnapshot("Projekt", null, null, null,
                    CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, null, null, null), List.of());
            assertThat(generationValidator.validate(client.generatePlan(noDates), noDates).isValid()).isTrue();
            assertThat(client.generatePlan(noDates)).isEqualTo(client.generatePlan(noDates));
        }
    }

    @Test
    void generatedDatesStayInsideConfirmedProjectPeriod() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITH_DATES);
        LocalDate projectStart = LocalDate.of(2026, 10, 10);
        LocalDate projectEnd = LocalDate.of(2026, 10, 12);
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Kurzes Projekt", null, projectStart, projectEnd,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null,
                null, null, null);

        var response = client.generatePlan(new AiGenerationRequest(snapshot, List.of()));

        assertThat(response.sections()).allSatisfy(section -> {
            assertThat(section.tasks()).allSatisfy(task -> {
                assertThat(task.startDate()).isBetween(projectStart, projectEnd);
                assertThat(task.dueDate()).isBetween(projectStart, projectEnd);
            });
            assertThat(section.milestones()).allSatisfy(milestone ->
                    assertThat(milestone.date()).isBetween(projectStart, projectEnd));
        });
    }

    private AiPreCheckRequest preCheckRequest() {
        return new AiPreCheckRequest(snapshot());
    }

    private AiGenerationRequest generationRequest() {
        return new AiGenerationRequest(snapshot(), List.of());
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Umzug planen", "Wohnungswechsel organisieren",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
                CollaborationMode.GROUP, ProjectCategory.HOME, ProjectSubCategory.MOVING,
                "Bis Monatsende umziehen", "Budget 2.000 Euro", "Kartons sind vorhanden");
    }
}

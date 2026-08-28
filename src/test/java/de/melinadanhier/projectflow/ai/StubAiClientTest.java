package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiGenerationScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiPreCheckScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiProperties;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
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
                    CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test", null, null, null), List.of());
            assertThat(generationValidator.validate(client.generatePlan(noDates), noDates).isValid()).isTrue();
            assertThat(client.generatePlan(noDates)).isEqualTo(client.generatePlan(noDates));
        }
    }

    @Test
    void providesAllPreCheckScenariosWithVersionedStructuredResponses() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.NO_PROBLEMS);
        assertThat(client.preCheck(preCheckRequest()).problems()).isEmpty();

        properties.setPreCheckScenario(StubAiPreCheckScenario.WARNING);
        assertThat(client.preCheck(preCheckRequest()).problems())
                .extracting("severity").containsExactly(AiPreCheckSeverity.WARNING);

        properties.setPreCheckScenario(StubAiPreCheckScenario.ERROR);
        assertThat(client.preCheck(preCheckRequest()).problems())
                .extracting("severity").containsExactly(AiPreCheckSeverity.ERROR);

        properties.setPreCheckScenario(StubAiPreCheckScenario.MULTIPLE_ISSUES);
        var multiple = client.preCheck(preCheckRequest());
        assertThat(multiple.problems()).extracting("severity")
                .containsExactly(AiPreCheckSeverity.WARNING, AiPreCheckSeverity.ERROR);
    }

    @Test
    void generatesMultiplePhasesTasksAndMilestonesWithDates() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITH_DATES);

        var response = client.generatePlan(generationRequest());

        assertThat(response.phases()).hasSize(2);
        assertThat(response.phases()).allSatisfy(phase -> {
            assertThat(phase.startDate()).isNotNull();
            assertThat(phase.endDate()).isNotNull();
            assertThat(phase.tasks()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(phase.milestones()).isNotEmpty();
            assertThat(phase.tasks()).allSatisfy(task -> {
                assertThat(task.startDate()).isNotNull();
                assertThat(task.dueDate()).isNotNull();
            });
            assertThat(phase.milestones()).allSatisfy(milestone -> assertThat(milestone.date()).isNotNull());
        });
    }

    @Test
    void generatesTheSameCentralStructureWithoutAnyDates() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITHOUT_DATES);

        var response = client.generatePlan(generationRequest());

        assertThat(response.phases()).hasSize(2).allSatisfy(phase -> {
            assertThat(phase.startDate()).isNull();
            assertThat(phase.endDate()).isNull();
            assertThat(phase.tasks()).allSatisfy(task -> {
                assertThat(task.startDate()).isNull();
                assertThat(task.dueDate()).isNull();
            });
            assertThat(phase.milestones()).allSatisfy(milestone -> assertThat(milestone.date()).isNull());
        });
    }

    @Test
    void generatedDatesStayInsideConfirmedProjectPeriod() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITH_DATES);
        LocalDate projectStart = LocalDate.of(2026, 10, 10);
        LocalDate projectEnd = LocalDate.of(2026, 10, 12);
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Kurzes Projekt", null, projectStart, projectEnd,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);

        var response = client.generatePlan(new AiGenerationRequest(snapshot, List.of()));

        assertThat(response.phases().getFirst().startDate()).isEqualTo(projectStart);
        assertThat(response.phases()).allSatisfy(phase -> {
            assertThat(phase.startDate()).isBetween(projectStart, projectEnd);
            assertThat(phase.endDate()).isBetween(projectStart, projectEnd);
            assertThat(phase.tasks()).allSatisfy(task -> {
                assertThat(task.startDate()).isBetween(projectStart, projectEnd);
                assertThat(task.dueDate()).isBetween(projectStart, projectEnd);
            });
            assertThat(phase.milestones()).allSatisfy(milestone ->
                    assertThat(milestone.date()).isBetween(projectStart, projectEnd));
        });
    }

    @Test
    void datedScenarioWithoutProjectStartFallsBackToPlanWithoutDates() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITH_DATES);
        AiWizardSnapshot snapshot = new AiWizardSnapshot(
                "Projekt ohne Terminbasis", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);

        var response = client.generatePlan(new AiGenerationRequest(snapshot, List.of()));

        assertThat(response.phases()).allSatisfy(phase -> {
            assertThat(phase.startDate()).isNull();
            assertThat(phase.endDate()).isNull();
            assertThat(phase.tasks()).allSatisfy(task -> {
                assertThat(task.startDate()).isNull();
                assertThat(task.dueDate()).isNull();
            });
            assertThat(phase.milestones()).allSatisfy(milestone -> assertThat(milestone.date()).isNull());
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
                CollaborationMode.GROUP, TemplateCategory.HOME, "Umzug",
                "Bis Monatsende umziehen", "Budget 2.000 Euro", "Kartons sind vorhanden");
    }
}

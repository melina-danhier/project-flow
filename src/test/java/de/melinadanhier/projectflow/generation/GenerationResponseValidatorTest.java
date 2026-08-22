package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedMilestone;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanMetadata;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;
import de.melinadanhier.projectflow.generation.validation.GenerationResponseValidator;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationResponseValidatorTest {

    private final GenerationResponseValidator validator = new GenerationResponseValidator(
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void acceptsSmallUndatedPlanAndRejectsEmptyPlan() {
        assertThat(validator.validate(validPlan(null, null, 1), request()).valid()).isTrue();

        var result = validator.validate(
                new GeneratedPlanResponse(new GeneratedPlanMetadata("Plan", List.of()), List.of()), request());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting("code").contains("PHASE_MISSING", "TASK_MISSING");
    }

    @Test
    void collectsInvalidEffortDateOrderRangeAndMixedPlanning() {
        LocalDate beforeProject = LocalDate.of(2026, 8, 31);
        LocalDate afterStart = LocalDate.of(2026, 9, 4);
        var result = validator.validate(validPlan(afterStart, beforeProject, 0), request());

        assertThat(result.issues()).extracting("code").contains(
                "TASK_EFFORT_INVALID",
                "TASK_DATES_INVALID",
                "TASK_DATE_OUTSIDE_PROJECT");
    }

    @Test
    void beanValidationCoversLongBlankAndNestedInvalidValues() {
        GeneratedTask nestedInvalid = new GeneratedTask(
                "task-1", "Aufgabe", null, 1, null, null, "x".repeat(2001),
                GeneratedElementOrigin.AI_INFERRED, 1);
        GeneratedPlanResponse response = new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", " ", null, null, null, 1,
                        List.of(nestedInvalid), List.of())));

        var result = validator.validate(response, request());

        assertThat(result.issues()).extracting("code").contains("BEAN_VALIDATION_FAILED");
        assertThat(result.issues()).extracting(
                        de.melinadanhier.projectflow.generation.validation.GenerationValidationIssue::message)
                .anyMatch(message -> message.contains("phases[0].title"))
                .anyMatch(message -> message.contains("criticalAssumption"));

        GeneratedPlanResponse longTitle = new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "x".repeat(101), null, null, null, 1,
                        List.of(new GeneratedTask(
                                "task-1", "Aufgabe", null, 1, null, null, null,
                                GeneratedElementOrigin.AI_INFERRED, 1)), List.of())));
        assertThat(validator.validate(longTitle, request()).issues()).extracting("code")
                .contains("BEAN_VALIDATION_FAILED");
    }

    @Test
    void acceptsDueDateOnlyAndRejectsDatesOutsidePhase() {
        LocalDate dueDate = LocalDate.of(2026, 9, 10);
        GeneratedPlanResponse valid = new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null,
                        LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 15), 1,
                        List.of(new GeneratedTask(
                                "task-1", "Aufgabe", null, 1, null, dueDate, null,
                                GeneratedElementOrigin.AI_INFERRED, 1)),
                        List.of(new GeneratedMilestone("milestone-1", "Ziel", dueDate, 2)))));
        assertThat(validator.validate(valid, request()).valid()).isTrue();

        GeneratedPlanResponse outsidePhase = new GeneratedPlanResponse(
                valid.metadata(), List.of(new GeneratedPhase(
                "phase-1", "Phase", null,
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 8), 1,
                valid.phases().getFirst().tasks(), valid.phases().getFirst().milestones())));
        assertThat(validator.validate(outsidePhase, request()).issues()).extracting("code")
                .contains("ELEMENT_DATE_OUTSIDE_PHASE");
    }

    private GeneratedPlanResponse validPlan(LocalDate taskStart, LocalDate taskEnd, int effort) {
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null, null, null, 1,
                        List.of(new GeneratedTask(
                                "task-1", "Aufgabe", null, effort,
                                taskStart, taskEnd, null, GeneratedElementOrigin.AI_INFERRED, 1)),
                        List.of(new GeneratedMilestone("milestone-1", "Ziel", null, 1)))));
    }

    private AiGenerationRequest request() {
        return new AiGenerationRequest(new AiWizardSnapshot(
                "Projekt", null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null), List.of());
    }
}

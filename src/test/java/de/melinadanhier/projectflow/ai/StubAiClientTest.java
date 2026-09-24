package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeApplicability;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblemType;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiClient;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiGenerationScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiPreCheckScenario;
import de.melinadanhier.projectflow.ai.provider.stub.StubAiProperties;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.ai.validation.improvement.AiImprovementResponseValidator;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubAiClientTest {

    private final StubAiProperties properties = new StubAiProperties();
    private final StubAiClient client = new StubAiClient(properties);

    @Test
    void normalScenariosAreDeterministicAndPassSharedValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var generationValidator = new GenerationResponseValidator(factory.getValidator());
            var preCheckValidator = new PreCheckResultValidator(factory.getValidator());
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
                assertThat(task.estimatedMinutes()).isPositive();
                assertThat(task.priority()).isNotNull();
            });
            assertThat(section.milestones()).allSatisfy(milestone ->
                    assertThat(milestone.date()).isBetween(projectStart, projectEnd));
        });
    }

    @Test
    void preCheckRiskScenarioReturnsExpectedProblem() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.RISK);
        var result = client.preCheck(preCheckRequest());
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.problems()).hasSize(1);
        var problem = result.problems().getFirst();
        assertThat(problem.severity()).isEqualTo(AiPreCheckSeverity.WARNING);
        assertThat(problem.type()).isEqualTo(AiPreCheckProblemType.RISK);
        assertThat(problem.proposedInputChanges()).isEmpty();
    }

    @Test
    void preCheckAssumptionScenarioReturnsExpectedProblem() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.ASSUMPTION);
        var result = client.preCheck(preCheckRequest());
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.problems()).hasSize(1);
        var problem = result.problems().getFirst();
        assertThat(problem.severity()).isEqualTo(AiPreCheckSeverity.WARNING);
        assertThat(problem.type()).isEqualTo(AiPreCheckProblemType.ASSUMPTION);
        assertThat(problem.proposedInputChanges()).isEmpty();
    }

    @Test
    void preCheckCriticalAssumptionMatchesSnapshotWorkingTime() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.CRITICAL_ASSUMPTION);
        var snapshot = new AiWizardSnapshot("Umzug", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.HOME, ProjectSubCategory.MOVING,
                "Umziehen", null, null, null, "4 Stunden pro Woche");
        var result = client.preCheck(new AiPreCheckRequest(snapshot));
        assertThat(result.problems()).hasSize(1);
        var problem = result.problems().getFirst();
        assertThat(problem.type()).isEqualTo(AiPreCheckProblemType.CRITICAL_ASSUMPTION);
        assertThat(problem.proposedInputChanges()).hasSize(1);
        var change = problem.proposedInputChanges().getFirst();
        assertThat(change.field()).isEqualTo("availableWorkingTime");
        assertThat(change.previousValue()).isEqualTo("4 Stunden pro Woche");
        assertThat(change.newValue()).isEqualTo("12 Stunden pro Woche");
    }

    @Test
    void preCheckCriticalAssumptionMatchesSnapshotEndDate() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.CRITICAL_ASSUMPTION);
        var result = client.preCheck(preCheckRequest());
        assertThat(result.problems()).hasSize(1);
        var problem = result.problems().getFirst();
        assertThat(problem.type()).isEqualTo(AiPreCheckProblemType.CRITICAL_ASSUMPTION);
        assertThat(problem.proposedInputChanges()).hasSize(1);
        var change = problem.proposedInputChanges().getFirst();
        assertThat(change.field()).isEqualTo("endDate");
        assertThat(change.previousValue()).isEqualTo("2026-09-21");
        assertThat(change.newValue()).isEqualTo("2026-10-05");
    }

    @Test
    void preCheckConflictScenarioReturnsExpectedError() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.CONFLICT);
        var result = client.preCheck(preCheckRequest());
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.problems()).hasSize(1);
        var problem = result.problems().getFirst();
        assertThat(problem.severity()).isEqualTo(AiPreCheckSeverity.ERROR);
        assertThat(problem.type()).isEqualTo(AiPreCheckProblemType.CONFLICT);
        assertThat(problem.acceptedInterpretation()).isEmpty();
    }

    @Test
    void preCheckMultipleWarningsReturnsAllThreeWarningTypes() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.MULTIPLE_WARNINGS);
        var result = client.preCheck(preCheckRequest());
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.problems()).hasSize(3);
        assertThat(result.problems().stream().map(de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem::type))
                .containsExactly(AiPreCheckProblemType.RISK, AiPreCheckProblemType.ASSUMPTION, AiPreCheckProblemType.CRITICAL_ASSUMPTION);
    }

    @Test
    void preCheckDynamicScenarioReactsToContentKeywords() {
        properties.setPreCheckScenario(StubAiPreCheckScenario.DYNAMIC);

        var conflictSnap = new AiWizardSnapshot("Konflikt im Team", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, null, "Unlösbarer Fehler", null);
        assertThat(client.preCheck(new AiPreCheckRequest(conflictSnap)).hasErrors()).isTrue();

        var warnSnap = new AiWizardSnapshot("Zeit ist knapp", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, null, null, null);
        assertThat(client.preCheck(new AiPreCheckRequest(warnSnap)).problems().getFirst().type())
                .isEqualTo(AiPreCheckProblemType.RISK);

        var critSnap = new AiWizardSnapshot("Kritischer Zeitplan", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, "Umfang anpassen", null, null);
        assertThat(client.preCheck(new AiPreCheckRequest(critSnap)).problems().getFirst().type())
                .isEqualTo(AiPreCheckProblemType.CRITICAL_ASSUMPTION);

        var neutralSnap = new AiWizardSnapshot("Normales Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER, null, null, null, null);
        assertThat(client.preCheck(new AiPreCheckRequest(neutralSnap)).problems()).isEmpty();
    }

    @Test
    void generatedPlanHasRealisticMinutesPrioritiesAndDependencies() {
        properties.setGenerationScenario(StubAiGenerationScenario.WITH_DATES);
        var plan = client.generatePlan(generationRequest());

        assertThat(plan.sections()).hasSize(3);
        var allTasks = plan.sections().stream().flatMap(s -> s.tasks().stream()).toList();
        assertThat(allTasks).hasSize(6);
        assertThat(allTasks).allSatisfy(task -> {
            assertThat(task.estimatedMinutes()).isGreaterThanOrEqualTo(60);
            assertThat(task.priority()).isIn(TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW);
        });

        // Dependencies exist and point to valid earlier tasks
        var taskWithPrereq = allTasks.stream().filter(t -> !t.prerequisiteTaskTempIds().isEmpty()).toList();
        assertThat(taskWithPrereq).isNotEmpty();
    }

    @Test
    void generatedPlanAdaptsToCategoryMovingAndEducation() {
        var movingSnap = new AiWizardSnapshot("Umzug", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.HOME, ProjectSubCategory.MOVING,
                null, null, null);
        var movingPlan = client.generatePlan(new AiGenerationRequest(movingSnap, List.of()));
        assertThat(movingPlan.sections().getFirst().title()).contains("Vorbereitung");
        assertThat(movingPlan.sections().get(1).title()).contains("Packen");

        var eduSnap = new AiWizardSnapshot("Referat", null, null, null,
                CollaborationMode.INDIVIDUAL, ProjectCategory.EDUCATION, ProjectSubCategory.PRESENTATION_OR_REPORT,
                null, null, null);
        var eduPlan = client.generatePlan(new AiGenerationRequest(eduSnap, List.of()));
        assertThat(eduPlan.sections().getFirst().title()).contains("Recherche");
        assertThat(eduPlan.sections().get(1).title()).contains("Ausarbeitung");
    }

    @Test
    void improveElementValidatesForAllActions() {
        var validator = new AiImprovementResponseValidator();
        var taskContent = new AiImprovementContent(AiImprovementElementType.TASK, "Aufgabe", "Beschreibung",
                TaskPriority.MEDIUM, 60, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

        for (var action : AiFeedbackType.values()) {
            var request = new AiImprovementRequest(action, null, null, null, null, taskContent);
            var response = client.improveElement(request);
            var validated = validator.validate(AiImprovementElementType.TASK, taskContent, action, null, response);
            assertThat(validated).isNotNull();
        }

        var milestoneContent = new AiImprovementContent(AiImprovementElementType.MILESTONE, "Meilenstein", null,
                null, null, null, LocalDate.of(2026, 9, 5));
        var replanMilestone = new AiImprovementRequest(AiFeedbackType.REPLAN, null, null, null, null, milestoneContent);
        var milestoneResp = client.improveElement(replanMilestone);
        assertThat(validator.validate(AiImprovementElementType.MILESTONE, milestoneContent, AiFeedbackType.REPLAN, null, milestoneResp))
                .isNotNull();
    }

    @Test
    void proposePlanChangesCoversApplicableAndRejection() {
        var validator = new AiPlanChangeResponseValidator();
        var planContext = new AiImprovementPlanContext(List.of(
                new AiImprovementPlanContext.Section("sec-1", "Vorbereitung", "Beschreibung", 100, List.of(
                        new AiImprovementPlanContext.Element(
                                AiImprovementElementType.TASK, "task-1", "Schritt", "Beschreibung", 100,
                                TaskPriority.MEDIUM, 60, null,
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), false, List.of())
                ))
        ));

        // Applicable: new task
        var applicableReq = new AiPlanChangeRequest("Neue Aufgabe einfügen", null, planContext);
        var applicableResp = client.proposePlanChanges(applicableReq);
        assertThat(applicableResp.applicability()).isEqualTo(AiPlanChangeApplicability.APPLICABLE);
        assertThat(validator.validate(applicableResp, planContext, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isNotNull();

        // Applicable: new section
        var sectionReq = new AiPlanChangeRequest("Neuen Bereich ergänzen", null, planContext);
        var sectionResp = client.proposePlanChanges(sectionReq);
        assertThat(sectionResp.applicability()).isEqualTo(AiPlanChangeApplicability.APPLICABLE);
        assertThat(sectionResp.sections()).hasSize(1);
        assertThat(validator.validate(sectionResp, planContext, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isNotNull();

        var milestoneResp = client.proposePlanChanges(new AiPlanChangeRequest("Meilenstein ergänzen", null, planContext));
        assertThat(milestoneResp.milestones()).hasSize(1);
        assertThat(validator.validate(milestoneResp, planContext, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isNotNull();

        var emptyContext = new AiImprovementPlanContext(List.of());
        var emptyPlanResp = client.proposePlanChanges(new AiPlanChangeRequest("Aufgabe ergänzen", null, emptyContext));
        assertThat(validator.validate(emptyPlanResp, emptyContext, null, null)).isNotNull();

        // Not applicable: rejected wish
        var rejectedReq = new AiPlanChangeRequest("Tägliche Yoga Dehnroutine einbauen (nicht passend)", null, planContext);
        var rejectedResp = client.proposePlanChanges(rejectedReq);
        assertThat(rejectedResp.applicability()).isEqualTo(AiPlanChangeApplicability.NOT_APPLICABLE);
        assertThat(rejectedResp.rejectionReason()).isNotBlank();
        assertThat(validator.validate(rejectedResp, planContext, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isNotNull();
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

package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationIssue;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationResult;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GenerationResponseValidatorTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 9, 30);

    private final GenerationResponseValidator validator = new GenerationResponseValidator(
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void handlesMissingResponseRequestAndWizardDataWithoutNullPointerException() {
        assertCodes(validator.validate(null, scheduledRequest()), RESPONSE_MISSING);
        assertCodes(validator.validate(validDatedPlan(), null), REQUEST_MISSING);
        AiGenerationRequest malformedRequest = mock(AiGenerationRequest.class);
        assertCodes(validator.validate(validDatedPlan(), malformedRequest),
                WIZARD_DATA_MISSING);
    }

    @Test
    void rejectsMissingSectionsAndTasks() {
        assertCodes(validator.validate(
                new GeneratedPlanResponse(List.of()),
                scheduledRequest()), SECTION_MISSING, TASK_MISSING);
        assertCodes(validator.validate(plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(), List.of())), scheduledRequest()), SECTION_TASK_MISSING, TASK_MISSING);
    }

    @Test
    void beanValidationIsPerformedByGenerationValidator() {
        GeneratedTask invalid = task("task-1", " ", 1, PROJECT_START, PROJECT_START);
        var result = validator.validate(plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(invalid), List.of())), scheduledRequest());

        assertCodes(result, BEAN_VALIDATION_FAILED, TASK_TITLE_MISSING);
        assertThat(result.issues()).filteredOn(issue -> issue.code() == BEAN_VALIDATION_FAILED)
                .extracting(GenerationValidationIssue::fieldPath).contains("sections[0].tasks[0].title");
    }

    @Test
    void rejectsMissingAndDuplicateTemporaryTaskIds() {
        GeneratedSection section = section("same-id", 1, PROJECT_START, PROJECT_END, List.of(
                task("same-id", "Aufgabe 1", 1, PROJECT_START, PROJECT_START),
                task("same-id", "Aufgabe 2", 2, PROJECT_START, PROJECT_START),
                task(" ", "Aufgabe 3", 3, PROJECT_START, PROJECT_START)), List.of());
        assertCodes(validator.validate(plan(section), scheduledRequest()),
                TEMP_ID_MISSING, TEMP_ID_DUPLICATE);
    }

    @Test
    void sectionAndMilestoneIdsAreNotPartOfTheTaskReferenceNamespace() {
        GeneratedSection section = section("shared", 1, PROJECT_START, PROJECT_END, List.of(
                task("shared", "Aufgabe 1", 1, PROJECT_START, PROJECT_START),
                task("task-2", "Aufgabe 2", 2, PROJECT_START, PROJECT_START),
                task("task-3", "Aufgabe 3", 3, PROJECT_START, PROJECT_START)),
                List.of(milestone("shared", 1, PROJECT_END)));
        assertThat(validator.validate(plan(section), scheduledRequest()).isValid()).isTrue();
    }

    @Test
    void rejectsUnknownSelfAndCyclicDependenciesTogether() {
        GeneratedTask first = task("task-1", "Eins", 1, PROJECT_START, PROJECT_START,
                List.of("task-2", "missing"));
        GeneratedTask second = task("task-2", "Zwei", 2, PROJECT_START, PROJECT_START,
                List.of("task-1"));
        GeneratedTask third = task("task-3", "Drei", 3, PROJECT_START, PROJECT_START,
                List.of("task-3"));

        var result = validator.validate(plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(first, second, third), List.of())), scheduledRequest());

        assertCodes(result, UNKNOWN_TASK_REFERENCE, SELF_DEPENDENCY, DEPENDENCY_CYCLE);
        assertThat(result.issues()).filteredOn(issue -> issue.code() == UNKNOWN_TASK_REFERENCE)
                .allMatch(issue -> issue.fieldPath().contains("prerequisiteTaskTempIds"));
    }

    @Test
    void rejectsInvalidAndDuplicateOrdersWithinTheirScopes() {
        GeneratedSection first = section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(
                        task("task-1", "Eins", 0, PROJECT_START, PROJECT_START),
                        task("task-2", "Zwei", 2, PROJECT_START, PROJECT_START),
                        task("task-3", "Drei", 2, PROJECT_START, PROJECT_START)),
                List.of(
                        milestone("milestone-1", 1, PROJECT_START),
                        milestone("milestone-2", 1, PROJECT_START)));
        GeneratedSection second = section("section-2", 1, PROJECT_START, PROJECT_END,
                List.of(task("task-4", "Vier", 1, PROJECT_START, PROJECT_START)), List.of());

        assertCodes(validator.validate(plan(first, second), scheduledRequest()),
                TASK_ORDER_INVALID, TASK_ORDER_DUPLICATE,
                MILESTONE_ORDER_DUPLICATE, SECTION_ORDER_DUPLICATE);
    }

    @Test
    void scheduledTasksRequireOnlyTheDueDate() {
        GeneratedSection section = section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(
                        task("task-1", "Nur Start", 1, PROJECT_START, null),
                        task("task-2", "Nur Ende", 2, null, PROJECT_END),
                        task("task-3", "Nur Ende", 3, null, PROJECT_END)), List.of());

        assertThat(validator.validate(plan(section), scheduledRequest()).issues())
                .filteredOn(issue -> issue.code() == TASK_DUE_DATE_MISSING)
                .singleElement().satisfies(issue -> assertThat(issue.fieldPath())
                        .isEqualTo("sections[0].tasks[0].dueDate"));
    }

    @Test
    void rejectsDatesOutsideProjectTimeFrame() {
        LocalDate before = PROJECT_START.minusDays(1);
        LocalDate after = PROJECT_END.plusDays(1);
        GeneratedSection section = section("section-1", 1, before, after,
                List.of(task("task-1", "Aufgabe", 1, before, after)),
                List.of(milestone("milestone-1", 1, after)));

        assertCodes(validator.validate(plan(section), scheduledRequest()),
                TASK_DATE_OUTSIDE_PROJECT, MILESTONE_DATE_OUTSIDE_PROJECT);
    }

    @Test
    void scheduledMilestonesRequireDatesWhileUndatedPlansMayContainDates() {
        GeneratedSection scheduled = section("section-1", 1, null, null, validTasks(),
                List.of(milestone("milestone-1", 1, null)));
        assertCodes(validator.validate(plan(scheduled), scheduledRequest()), MILESTONE_DATE_MISSING);

        GeneratedSection undated = section("section-1", 1, null, null,
                List.of(
                        task("task-1", "Aufgabe 1", 1, PROJECT_START, PROJECT_START),
                        task("task-2", "Aufgabe 2", 2, null, null),
                        task("task-3", "Aufgabe 3", 3, null, PROJECT_END)),
                List.of(milestone("milestone-1", 1, PROJECT_END)));
        assertThat(validator.validate(plan(undated), undatedRequest()).isValid()).isTrue();
    }

    @Test
    void rejectsReversedTaskDates() {
        GeneratedSection section = section("section-1", 1, PROJECT_END, PROJECT_START, List.of(
                task("task-1", "Aufgabe 1", 1, PROJECT_END, PROJECT_START),
                task("task-2", "Aufgabe 2", 2, PROJECT_START, PROJECT_END),
                task("task-3", "Aufgabe 3", 3, PROJECT_START, PROJECT_END)), List.of());
        assertCodes(validator.validate(plan(section), scheduledRequest()), TASK_DATES_INVALID);
    }

    @Test
    void validatesDuplicateAndCrossSectionDependencies() {
        GeneratedSection first = section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(task("task-1", "Eins", 1, null, PROJECT_START),
                        task("task-2", "Zwei", 2, null, PROJECT_START),
                        task("task-3", "Drei", 3, null, PROJECT_START)), List.of());
        GeneratedSection validSecond = section("section-2", 2, PROJECT_START, PROJECT_END,
                List.of(task("task-4", "Vier", 1, PROJECT_START, PROJECT_END,
                        List.of("task-1"))), List.of());
        assertThat(validator.validate(plan(first, validSecond), scheduledRequest()).isValid()).isTrue();

        GeneratedSection second = section("section-2", 2, PROJECT_START, PROJECT_END,
                List.of(task("task-4", "Vier", 1, PROJECT_START, PROJECT_END,
                        List.of("task-1", "task-1"))), List.of());
        var result = validator.validate(plan(first, second), scheduledRequest());
        assertCodes(result, DEPENDENCY_DUPLICATE);
        assertThat(result.issues()).noneMatch(issue -> issue.code() == UNKNOWN_TASK_REFERENCE);
    }

    @Test
    void detectsIndirectCyclesAcrossSections() {
        GeneratedSection first = section("section-1", 1, null, null, List.of(
                task("task-1", "Eins", 1, null, null, List.of("task-3")),
                task("task-2", "Zwei", 2, null, null, List.of("task-1"))), List.of());
        GeneratedSection second = section("section-2", 2, null, null,
                List.of(task("task-3", "Drei", 1, null, null, List.of("task-2"))), List.of());
        assertCodes(validator.validate(plan(first, second), undatedRequest()), DEPENDENCY_CYCLE);
    }

    @Test
    void validatesDependencyDatesWithStartAndDueDateFallback() {
        GeneratedTask prerequisite = task("task-1", "Voraussetzung", 1, null,
                PROJECT_START.plusDays(5));
        GeneratedTask earlyStart = task("task-2", "Zu frueh", 2, PROJECT_START.plusDays(4),
                PROJECT_START.plusDays(8), List.of("task-1"));
        GeneratedTask earlyDue = task("task-3", "Auch zu frueh", 3, null,
                PROJECT_START.plusDays(4), List.of("task-1"));
        var invalid = validator.validate(plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(prerequisite, earlyStart, earlyDue), List.of())), scheduledRequest());
        assertThat(invalid.issues()).filteredOn(issue -> issue.code() == DEPENDENCY_DATE_ORDER_INVALID)
                .hasSize(2).allMatch(issue -> issue.fieldPath().contains("prerequisiteTaskTempIds"));

        GeneratedTask equalStart = task("task-2", "Gleich", 2, PROJECT_START.plusDays(5),
                PROJECT_START.plusDays(5), List.of("task-1"));
        GeneratedTask equalDue = task("task-3", "Gleich faellig", 3, null,
                PROJECT_START.plusDays(5), List.of("task-1"));
        var valid = validator.validate(plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(prerequisite, equalStart, equalDue), List.of())), scheduledRequest());
        assertThat(valid.issues()).noneMatch(issue -> issue.code() == DEPENDENCY_DATE_ORDER_INVALID);
    }

    @Test
    void rejectsBlankOptionalDescriptionsAndOutOfRangeEffort() {
        GeneratedTask invalidTask = new GeneratedTask("task-1", "Eins", "  ", 10_001,
                null, null, null, GeneratedElementOrigin.AI_INFERRED, 1, List.of(), TaskPriority.HIGH);
        GeneratedSection section = new GeneratedSection(null, "Section", " ", 1,
                List.of(invalidTask, task("task-2", "Zwei", 2, null, null),
                        task("task-3", "Drei", 3, null, null)), List.of());
        assertCodes(validator.validate(plan(section), undatedRequest()),
                SECTION_DESCRIPTION_BLANK, TASK_DESCRIPTION_BLANK, TASK_EFFORT_INVALID);
    }

    @Test
    void enforcesAllCollectionProtectionLimits() {
        List<GeneratedSection> tooManySections = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> section("section-" + index, index, null, null,
                        List.of(task("section-task-" + index, "Aufgabe", 1, null, null)), List.of()))
                .toList();
        assertCodes(validator.validate(new GeneratedPlanResponse(tooManySections), undatedRequest()),
                SECTION_LIMIT_EXCEEDED);

        List<GeneratedTask> tooManyTasks = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> task("task-" + index, "Aufgabe " + index, index, null, null))
                .toList();
        assertCodes(validator.validate(plan(section("section-1", 1, null, null,
                tooManyTasks, List.of())), undatedRequest()), TASK_LIMIT_EXCEEDED);

        List<GeneratedMilestone> tooManyMilestones = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> milestone("milestone-" + index, index, null)).toList();
        assertCodes(validator.validate(plan(section("section-1", 1, null, null,
                validTasks(), tooManyMilestones)), undatedRequest()), MILESTONE_LIMIT_EXCEEDED);

        List<String> tooManyDependencies = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> "task-1").toList();
        GeneratedTask dependent = task("task-2", "Zwei", 2, null, null, tooManyDependencies);
        assertCodes(validator.validate(plan(section("section-1", 1, null, null,
                List.of(task("task-1", "Eins", 1, null, null), dependent,
                        task("task-3", "Drei", 3, null, null)), List.of())), undatedRequest()),
                DEPENDENCY_LIMIT_EXCEEDED);
    }

    @Test
    void acceptsFullyValidDatedAndUndatedPlans() {
        assertThat(validator.validate(validDatedPlan(), scheduledRequest()).isValid()).isTrue();
        GeneratedSection undated = section("section-1", 1, null, null,
                List.of(
                        task("task-1", "Aufgabe 1", 1, null, null),
                        task("task-2", "Aufgabe 2", 2, null, null),
                        task("task-3", "Aufgabe 3", 3, null, null)), List.of());
        assertThat(validator.validate(plan(undated), undatedRequest()).isValid()).isTrue();
    }

    @Test
    void validationCallsDoNotShareTasksCountsDatesOrIssues() {
        assertThat(validator.validate(validDatedPlan(), scheduledRequest()).isValid()).isTrue();

        GeneratedSection undated = section("section-1", 1, null, null, List.of(
                task("task-1", "Eins", 1, null, null),
                task("task-2", "Zwei", 2, null, null),
                task("task-3", "Drei", 3, null, null)), List.of());
        assertThat(validator.validate(plan(undated), undatedRequest()).isValid()).isTrue();

        GeneratedSection otherPlan = section("other-section", 1, null, null,
                List.of(task("other-task", "Andere Aufgabe", 1, null, null, List.of("task-1"))), List.of());
        assertCodes(validator.validate(plan(otherPlan), undatedRequest()),
                UNKNOWN_TASK_REFERENCE, TASK_COUNT_TOO_LOW);

        assertThat(validator.validate(validDatedPlan(), scheduledRequest()).isValid()).isTrue();
    }

    private GeneratedPlanResponse validDatedPlan() {
        return plan(section("section-1", 1, PROJECT_START, PROJECT_END,
                List.of(
                        task("task-1", "Aufgabe 1", 1, PROJECT_START, PROJECT_END),
                        task("task-2", "Aufgabe 2", 2, PROJECT_START, PROJECT_END),
                        task("task-3", "Aufgabe 3", 3, PROJECT_START, PROJECT_END)),
                List.of(milestone("milestone-1", 1, PROJECT_END))));
    }

    private GeneratedPlanResponse plan(GeneratedSection... sections) {
        return new GeneratedPlanResponse(List.of(sections));
    }

    private GeneratedSection section(String id, int order, LocalDate start, LocalDate end,
                                 List<GeneratedTask> tasks, List<GeneratedMilestone> milestones) {
        return new GeneratedSection(id, "Section", null, order, tasks, milestones);
    }

    private GeneratedTask task(String id, String title, int order, LocalDate start, LocalDate due) {
        return task(id, title, order, start, due, List.of());
    }

    private GeneratedTask task(String id, String title, int order, LocalDate start, LocalDate due,
                               List<String> prerequisites) {
        return new GeneratedTask(id, title, null, 1, start, due, null,
                GeneratedElementOrigin.AI_INFERRED, order, prerequisites);
    }

    private GeneratedMilestone milestone(String id, int order, LocalDate date) {
        return new GeneratedMilestone(id, "Meilenstein", date, order);
    }

    private List<GeneratedTask> validTasks() {
        return validTasks("task");
    }

    private List<GeneratedTask> validTasks(String prefix) {
        return List.of(
                task(prefix + "-1", "Aufgabe 1", 1, null, PROJECT_END),
                task(prefix + "-2", "Aufgabe 2", 2, null, PROJECT_END),
                task(prefix + "-3", "Aufgabe 3", 3, null, PROJECT_END));
    }

    private AiGenerationRequest scheduledRequest() {
        return request(PROJECT_START, PROJECT_END, AiProjectTimeFrameType.START_AND_END);
    }

    private AiGenerationRequest undatedRequest() {
        return request(null, null, AiProjectTimeFrameType.NONE);
    }

    private AiGenerationRequest request(LocalDate start, LocalDate end, AiProjectTimeFrameType type) {
        return new AiGenerationRequest(new AiWizardSnapshot(
                "Projekt", null, start, end,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, null, "Test",
                null, null, null, type, null), List.of());
    }

    private void assertCodes(GenerationValidationResult result, GenerationValidationCode... codes) {
        assertThat(result.issues()).extracting(GenerationValidationIssue::code).contains(codes);
    }
}

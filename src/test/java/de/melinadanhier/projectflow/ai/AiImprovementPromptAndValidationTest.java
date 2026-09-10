package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.AiResponseSchemas;
import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.prompt.ImprovementPromptBuilder;
import de.melinadanhier.projectflow.ai.validation.improvement.AiImprovementResponseValidator;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiImprovementPromptAndValidationTest {

    private final AiImprovementResponseValidator validator = new AiImprovementResponseValidator();

    @Test
    void improveKeepsMeaningInformationAndApproximateLengthAndChangesOnlyText() {
        var original = task(2, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
        var request = new AiImprovementRequest(AiFeedbackType.IMPROVE, null,
                new AiImprovementProjectContext("Umzug", null, null, null), original);
        var response = response("Kartons packen", "Kartons geordnet und sorgfältig packen", 2,
                original.startDate(), original.dueDate());

        AiImprovementContent result = validator.validate(
                AiImprovementElementType.TASK, original, AiFeedbackType.IMPROVE, null, response);

        assertThat(new ImprovementPromptBuilder(new ObjectMapper()).build(request).systemInstructions())
                .contains("Bedeutung", "Informationsgehalt", "ungefähre Länge");
        assertThat(result.title()).isNotEqualTo(original.title());
        assertThat(result.estimatedHours()).isEqualTo(original.estimatedHours());
        assertThat(result.startDate()).isEqualTo(original.startDate());
        assertThat(result.dueDate()).isEqualTo(original.dueDate());
    }

    @Test
    void improveRejectsSubstantialTextExpansion() {
        var original = task(2, null, null);
        var response = response("Packen", "Sehr viele neue Details ".repeat(20), 2, null, null);

        assertThatThrownBy(() -> validator.validate(
                AiImprovementElementType.TASK, original, AiFeedbackType.IMPROVE, null, response))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void simplifyPromptContainsOnlySelectedElementContext() throws Exception {
        var request = new AiImprovementRequest(AiFeedbackType.SIMPLIFY, null,
                null, null, null, task(null, null, null));

        Map<String, Object> data = promptData(request);

        assertThat(data.keySet()).containsExactlyInAnyOrder("action", "comment", "elementType", "element");
        assertThat(((Map<?, ?>) data.get("element")).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("title", "description");
    }

    @Test
    void replanPromptReceivesFullCurrentPlanAsReadOnlyContext() throws Exception {
        var plan = new AiImprovementPlanContext(SortMode.DATE, "task-1", List.of(new AiImprovementPlanContext.Section(
                "section-1", "Vorbereitung", "Alles vorbereiten", 1,
                List.of(
                        new AiImprovementPlanContext.Element(AiImprovementElementType.TASK,
                                "task-1", "Packen", null, 1, TaskPriority.MEDIUM, 2,
                                de.melinadanhier.projectflow.planelement.model.TaskStatus.OPEN,
                                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), null,
                                List.of("task-0")),
                        new AiImprovementPlanContext.Element(AiImprovementElementType.MILESTONE,
                                "milestone-1", "Bereit", null, 2, null, null, null,
                                null, LocalDate.of(2026, 9, 13), false, List.of())))));
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN, null,
                new AiImprovementProjectContext("Umzug", "In sechs Wochen", LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 13)), null, plan,
                task(2, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)));

        Map<String, Object> data = promptData(request);

        assertThat(data).containsKeys("project", "currentPlan", "element");
        assertThat(data.get("temporalContextAvailable")).isEqualTo(true);
        assertThat(data).doesNotContainKey("section");
        Map<?, ?> serializedPlan = (Map<?, ?>) data.get("currentPlan");
        assertThat(serializedPlan.get("selectedElementReference")).isEqualTo("task-1");
        Map<?, ?> serializedSection = (Map<?, ?>) ((List<?>) serializedPlan.get("sections")).getFirst();
        List<?> serializedElements = (List<?>) serializedSection.get("elements");
        assertThat(serializedElements).hasSize(2);
        Map<?, ?> serializedTask = (Map<?, ?>) serializedElements.getFirst();
        assertThat(serializedTask.get("elementType")).isEqualTo("TASK");
        assertThat(serializedTask.get("reference")).isEqualTo("task-1");
        assertThat(serializedTask.get("title")).isEqualTo("Packen");
        assertThat(serializedTask.get("position")).isEqualTo(1);
        assertThat(serializedTask.get("dueDate")).isEqualTo("2026-09-12");
        assertThat(serializedTask.get("prerequisiteReferences")).isEqualTo(List.of("task-0"));
        Map<?, ?> serializedMilestone = (Map<?, ?>) serializedElements.get(1);
        assertThat(serializedMilestone.get("elementType")).isEqualTo("MILESTONE");
        assertThat(serializedMilestone.get("reference")).isEqualTo("milestone-1");
        assertThat(serializedMilestone.get("title")).isEqualTo("Bereit");
        assertThat(serializedMilestone.get("position")).isEqualTo(2);
        assertThat(serializedMilestone.get("dueDate")).isEqualTo("2026-09-13");
        assertThat(new ImprovementPromptBuilder(new ObjectMapper()).build(request).systemInstructions())
                .contains("vollständigen", "selectedElementReference", "gemeinsam", "position", "logisch vor", "zu erreichende",
                        "nicht als Default", "sortOrder", "beforeElementId", "explanation",
                        "fachlichen Grund", "temporalContextAvailable=false", "keine Datumsänderung",
                        "Erfinde niemals absolute Datumswerte", "technischen Positionsangaben",
                        "internen Gedankengänge");
    }

    @Test
    void replanPromptMarksCompletelyUndatedContextAndRequiresNullDatesToRemainNull() throws Exception {
        var plan = new AiImprovementPlanContext(SortMode.MANUAL, "task-1",
                List.of(new AiImprovementPlanContext.Section("section-1", "Lernen", null, 1,
                        List.of(new AiImprovementPlanContext.Element(AiImprovementElementType.TASK,
                                "task-1", "Kapitel lesen", null, 1, TaskPriority.MEDIUM, 2,
                                de.melinadanhier.projectflow.planelement.model.TaskStatus.OPEN,
                                null, null, null, List.of())))));
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN, "In eine andere Phase verschieben",
                new AiImprovementProjectContext("Lernplan", "Inhalte sinnvoll gliedern", null, null),
                null, plan, task(2, null, null));

        Map<String, Object> data = promptData(request);

        assertThat(data.get("temporalContextAvailable")).isEqualTo(false);
        assertThat(new ImprovementPromptBuilder(new ObjectMapper()).build(request).systemInstructions())
                .contains("bestehende null-Datumswerte", "keine neuen", "Section oder Reihenfolge allein");
    }

    @Test
    void explicitAbsoluteDateInUserCommentProvidesTemporalContext() throws Exception {
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN,
                "Bitte bis zum 17.09.2026 einplanen",
                new AiImprovementProjectContext("Lernplan", null, null, null), null,
                new AiImprovementPlanContext(SortMode.MANUAL, "task-1", List.of()),
                task(2, null, null));

        assertThat(promptData(request).get("temporalContextAvailable")).isEqualTo(true);
    }

    @Test
    void estimateEffortUsesTaskSectionAndBasicProjectButNotFullPlan() throws Exception {
        var request = new AiImprovementRequest(AiFeedbackType.ESTIMATE_EFFORT, null,
                new AiImprovementProjectContext("Umzug", "In sechs Wochen", null, null),
                new AiImprovementSectionContext("Vorbereitung", null), null, task(2, null, null));

        Map<String, Object> data = promptData(request);

        assertThat(data).containsKeys("project", "section", "element").doesNotContainKey("currentPlan");
        assertThat(((Map<?, ?>) data.get("element")).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("title", "description", "estimatedHours");
    }

    @Test
    void actionSchemasStrictlyWhitelistOutputFields() {
        assertSchema(AiTextImprovementResponse.class, "title", "description");
        assertSchema(AiTaskReplanResponse.class, "startDate", "dueDate", "placement", "explanation");
        assertSchema(AiMilestoneReplanResponse.class, "dueDate", "placement", "explanation");
        assertSchema(AiTaskEffortResponse.class, "estimatedHours", "explanation");
    }

    @ParameterizedTest
    @EnumSource(value = AiFeedbackType.class, names = {"IMPROVE", "EXPAND", "SIMPLIFY"})
    void textActionsRejectEveryPlanningFieldChange(AiFeedbackType action) {
        var original = task(2, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
        var response = response("Besserer Titel", "Bessere Beschreibung", 3,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13));

        assertThatThrownBy(() -> validator.validate(
                AiImprovementElementType.TASK, original, action, null, response))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AiFeedbackType.class, names = {"IMPROVE", "EXPAND", "SIMPLIFY"})
    void textActionsKeepNullPlanningValuesNull(AiFeedbackType action) {
        var original = task(null, null, null);
        var response = response("Besserer Titel", "Bessere Beschreibung", null, null, null);

        AiImprovementContent result = validator.validate(
                AiImprovementElementType.TASK, original, action, null, response);

        assertThat(result.estimatedHours()).isNull();
        assertThat(result.startDate()).isNull();
        assertThat(result.dueDate()).isNull();
    }

    @Test
    void replanAllowsOnlySelectedTaskDates() {
        var original = task(2, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
        var response = response("Packen", "Kartons packen", 2,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14),
                AiReplanPlacementResponse.unchanged(),
                "Die Aufgabe sollte vor dem Transport abgeschlossen sein.");

        AiImprovementContent result = validator.validate(
                AiImprovementElementType.TASK, original, AiFeedbackType.REPLAN, null, response);

        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(result.title()).isEqualTo(original.title());
        assertThat(result.estimatedHours()).isEqualTo(2);
    }

    @Test
    void estimateEffortIsTaskOnlyAndAllowsOnlyEffort() {
        assertThat(AiFeedbackType.ESTIMATE_EFFORT.supports(AiImprovementElementType.TASK)).isTrue();
        assertThat(AiFeedbackType.ESTIMATE_EFFORT.supports(AiImprovementElementType.SECTION)).isFalse();
        assertThat(AiFeedbackType.ESTIMATE_EFFORT.supports(AiImprovementElementType.MILESTONE)).isFalse();

        var original = task(2, null, null);
        AiImprovementContent result = validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.ESTIMATE_EFFORT, null,
                response("Packen", "Kartons packen", 5, null, null,
                        "Der Umfang entspricht etwa fünf Arbeitsstunden."));
        assertThat(result.estimatedHours()).isEqualTo(5);

        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.ESTIMATE_EFFORT, null,
                response("Anderer Titel", "Kartons packen", 5, null, null, "Kurze Begründung")))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.ESTIMATE_EFFORT, null,
                response("Packen", "Kartons packen", null, null, null)))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void planningActionsRejectMissingAndOverlongExplanation() {
        var original = task(2, null, null);

        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.REPLAN, null,
                response("Packen", "Kartons packen", 2, null, null,
                        AiReplanPlacementResponse.unchanged(), null)))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("ungültige Planelementdaten");
        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.ESTIMATE_EFFORT, null,
                response("Packen", "Kartons packen", 5, null, null, "x".repeat(501))))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @Test
    void replanRequiresUnambiguousPlacementContract() {
        var original = task(2, null, null);
        String first = "00000000-0000-0000-0000-000000000001";
        String second = "00000000-0000-0000-0000-000000000002";

        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.REPLAN, null,
                response("Packen", "Kartons packen", 2, null, null, null, "Begründung")))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.REPLAN, null,
                response("Packen", "Kartons packen", 2, null, null,
                        new AiReplanPlacementResponse(true, null, first, second), "Begründung")))
                .isInstanceOf(AiOutputValidationException.class);
        assertThatThrownBy(() -> validator.validate(AiImprovementElementType.TASK, original,
                AiFeedbackType.REPLAN, null,
                response("Packen", "Kartons packen", 2, null, null,
                        new AiReplanPlacementResponse(false, first, null, null), "Begründung")))
                .isInstanceOf(AiOutputValidationException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> promptData(AiImprovementRequest request) throws Exception {
        var prompt = new ImprovementPromptBuilder(new ObjectMapper()).build(request);
        return (Map<String, Object>) new ObjectMapper().readValue(prompt.confirmedUserData(), Map.class);
    }

    private void assertSchema(Class<?> type, String... fields) {
        Map<String, Object> schema = AiResponseSchemas.forType(type);
        assertThat(((Map<?, ?>) schema.get("properties")).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder(fields);
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }

    private AiImprovementContent task(Integer effort, LocalDate start, LocalDate due) {
        return new AiImprovementContent(AiImprovementElementType.TASK, "Packen", "Kartons packen",
                TaskPriority.MEDIUM, effort, start, due);
    }

    private AiImprovementResponse response(
            String title, String description, Integer effort, LocalDate start, LocalDate due) {
        return response(title, description, effort, start, due, null);
    }

    private AiImprovementResponse response(
            String title, String description, Integer effort, LocalDate start, LocalDate due, String explanation) {
        return new AiImprovementResponse(AiImprovementElementType.TASK, title, description,
                TaskPriority.MEDIUM, effort, start, due, explanation);
    }

    private AiImprovementResponse response(
            String title, String description, Integer effort, LocalDate start, LocalDate due,
            AiReplanPlacementResponse placement, String explanation) {
        return new AiImprovementResponse(AiImprovementElementType.TASK, title, description,
                TaskPriority.MEDIUM, effort, start, due, placement, explanation);
    }
}

package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GeneratedPlanDraftMapperTest {
    private final GeneratedPlanDraftMapper mapper = new GeneratedPlanDraftMapper();

    @Test
    void resolvesForwardReferencesAcrossSectionsWithoutPersistingEntities() {
        var result = mapper.map(new GeneratedPlanResponse(List.of(
                section("p1", List.of(task("successor", List.of("prerequisite")))),
                section("p2", List.of(task("prerequisite", List.of()))))));
        var successor = (DraftTask) result.elements().getFirst();
        var prerequisite = (DraftTask) result.elements().getLast();
        assertThat(successor.getPrerequisites()).containsExactly(prerequisite);
        assertThat(successor.getDraftSection()).isSameAs(result.sections().getFirst());
        assertThat(prerequisite.getDraftSection()).isSameAs(result.sections().getLast());
        assertThat(result.elements()).allSatisfy(element -> {
            assertThat(element.getId()).isNull();
            assertThat(element.getDraftPlan()).isNull();
        });
    }

    @Test
    void rejectsUnresolvableReferencesWithExistingStableErrorCode() {
        assertInvalid(new GeneratedPlanResponse(List.of(section("p", List.of(task("task", List.of("missing")))))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "same"})
    void rejectsMissingOrAmbiguousTaskKeys(String key) {
        assertInvalid(new GeneratedPlanResponse(List.of(
                section("p1", List.of(task("same", List.of()))),
                section("p2", List.of(task(key, List.of()))))));
    }

    @Test
    void ignoresOptionalSectionAndMilestoneKeysOutsideTheTaskReferenceNamespace() {
        var result = mapper.map(new GeneratedPlanResponse(List.of(
                new GeneratedSection("same", "Section A", null, 1,
                        List.of(task("same", List.of())),
                        List.of(new GeneratedMilestone("same", "Milestone A", null, 2))),
                new GeneratedSection("same", "Section B", null, 2, List.of(),
                        List.of(new GeneratedMilestone("same", "Milestone B", null, 1))))));

        assertThat(result.sections()).hasSize(2);
        assertThat(result.elements()).hasSize(3);
    }

    @Test
    void sortsAndNormalizesSectionsAndMixedElementsForTheDraftModel() {
        var result = mapper.map(new GeneratedPlanResponse(List.of(
                new GeneratedSection("later", "Later", null, 5,
                        List.of(new GeneratedTask("late", "Late task", null, null, null, null,
                                GeneratedElementOrigin.AI_INFERRED, 4)),
                        List.of()),
                new GeneratedSection("earlier", "Earlier", null, 2,
                        List.of(new GeneratedTask("last", "Last", null, null, null, null,
                                        GeneratedElementOrigin.AI_INFERRED, 8),
                                new GeneratedTask("first", "First", null, null, null, null,
                                        GeneratedElementOrigin.AI_INFERRED, 2)),
                        List.of(new GeneratedMilestone(null, "Middle", null, 5))))));

        assertThat(result.sections()).extracting("title").containsExactly("Earlier", "Later");
        assertThat(result.sections()).extracting("sortOrder").containsExactly(0, 1);
        assertThat(result.sections().getFirst().getElements())
                .extracting(DraftPlanElement::getTitle)
                .containsExactly("First", "Middle", "Last");
        assertThat(result.sections().getFirst().getElements())
                .extracting(DraftPlanElement::getSortOrder)
                .containsExactly(0, 1, 2);
    }

    private void assertInvalid(GeneratedPlanResponse response) {
        assertThatThrownBy(() -> mapper.map(response))
                .isInstanceOfSatisfying(AiOutputValidationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.INVALID_AI_RESPONSE));
    }

    private GeneratedSection section(String id, List<GeneratedTask> tasks) {
        return new GeneratedSection(id, "Section", null, 1, tasks, List.of());
    }

    private GeneratedTask task(String id, List<String> prerequisites) {
        return new GeneratedTask(id, "Aufgabe", null, null, null, null,
                GeneratedElementOrigin.AI_INFERRED, 1, prerequisites);
    }
}

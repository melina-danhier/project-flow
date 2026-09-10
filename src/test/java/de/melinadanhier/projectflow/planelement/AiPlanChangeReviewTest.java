package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.*;
import de.melinadanhier.projectflow.ai.model.planchange.*;
import de.melinadanhier.projectflow.ai.validation.planchange.AiPlanChangeResponseValidator;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.planelement.dto.planchange.PlanChangeProposal;
import de.melinadanhier.projectflow.planelement.model.*;
import de.melinadanhier.projectflow.planelement.service.AiPlanChangeService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiPlanChangeReviewTest {
    @Test void unchangedSectionIsContextOnlyAndUnchangedSiblingIsAbsent() {
        String sectionId = UUID.randomUUID().toString(), changedId = UUID.randomUUID().toString();
        var changed = element(changedId, "Packen"); var sibling = element(UUID.randomUUID().toString(), "Unveränderte Aufgabe");
        var plan = new AiImprovementPlanContext(SortMode.MANUAL, null, List.of(
                new AiImprovementPlanContext.Section(sectionId, "Vorbereitung", null, 1, List.of(changed, sibling))));
        var task = new AiTaskChange(AiPlanChangeOperation.MODIFIED, changedId, sectionId, List.of("description"),
                null, "Neue Beschreibung", null, null, null, null, new AiRelativePlacement(null, null), null);
        var review = service().review(proposal(plan, new AiPlanChangeResponse("Kurz", List.of(), List.of(task), List.of())));
        assertThat(review.sections()).hasSize(1); assertThat(review.sections().getFirst().sectionChanged()).isFalse();
        assertThat(review.sections().getFirst().elements()).extracting(e -> e.title()).containsExactly("Packen");
        assertThat(review.sections().getFirst().elements().getFirst().fields()).extracting(f -> f.label()).containsExactly("Beschreibung");
    }
    @Test void newAndChangedElementsHaveCorrectOperationsAndMultipleSectionsRemainGrouped() {
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString(), milestoneId = UUID.randomUUID().toString();
        var plan = new AiImprovementPlanContext(SortMode.MANUAL, null, List.of(
                new AiImprovementPlanContext.Section(first, "Eins", null, 1, List.of(element(taskId, "Alt"))),
                new AiImprovementPlanContext.Section(second, "Zwei", null, 2, List.of(milestone(milestoneId, "Ziel")))));
        var task = new AiTaskChange(AiPlanChangeOperation.NEW, null, first, List.of("title", "priority"), "Neu", null,
                TaskPriority.HIGH, null, null, null, new AiRelativePlacement(null, null), null);
        var milestone = new AiMilestoneChange(AiPlanChangeOperation.MODIFIED, milestoneId, second, List.of("title"),
                "Neues Ziel", null, null, new AiRelativePlacement(null, null), null);
        var review = service().review(proposal(plan, new AiPlanChangeResponse("Kurz", List.of(), List.of(task), List.of(milestone))));
        assertThat(review.sections()).extracting(s -> s.title()).containsExactly("Eins", "Zwei");
        assertThat(review.sections().get(0).elements().getFirst().operation()).isEqualTo(AiPlanChangeOperation.NEW);
        assertThat(review.sections().get(1).elements().getFirst().operation()).isEqualTo(AiPlanChangeOperation.MODIFIED);
    }
    private AiPlanChangeService service() { return new AiPlanChangeService(mock(), mock(), mock(), mock(), new AiPlanChangeResponseValidator()); }
    private PlanChangeProposal proposal(AiImprovementPlanContext plan, AiPlanChangeResponse response) { return new PlanChangeProposal(
            UUID.randomUUID(), UUID.randomUUID(), "Projekt", "Wunsch", Instant.now(), plan, response); }
    private AiImprovementPlanContext.Element element(String id, String title) { return new AiImprovementPlanContext.Element(
            AiImprovementElementType.TASK, id, title, "Alt", 1, TaskPriority.MEDIUM, null, TaskStatus.OPEN,
            null, null, null, List.of()); }
    private AiImprovementPlanContext.Element milestone(String id, String title) { return new AiImprovementPlanContext.Element(
            AiImprovementElementType.MILESTONE, id, title, null, 1, null, null, null, null, null, false, List.of()); }
}

package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementProposal;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiImprovementReview;
import de.melinadanhier.projectflow.planelement.dto.improvement.AiReplanPlacementProposal;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiImprovementReviewTest {

    @Test
    void usesOnlyFieldsAllowedForTheSelectedAction() {
        AiImprovementContent original = content("Titel", "Alt", TaskPriority.LOW, 2,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        AiImprovementContent proposed = content("Neuer Titel", "Neu", TaskPriority.HIGH, 5,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));

        assertThat(AiImprovementReview.from(proposal(AiFeedbackType.IMPROVE, original, proposed)).changes())
                .extracting(change -> change.key())
                .containsExactly("title", "description");
        assertThat(AiImprovementReview.from(proposal(AiFeedbackType.ESTIMATE_EFFORT, original, proposed)).changes())
                .extracting(change -> change.key())
                .containsExactly("estimated-hours");
    }

    @Test
    void omitsEqualFieldsAndReportsAnEmptyDiff() {
        AiImprovementContent content = content("Titel", "Beschreibung", TaskPriority.MEDIUM, 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));

        AiImprovementReview review = AiImprovementReview.from(
                proposal(AiFeedbackType.IMPROVE, content, content));

        assertThat(review.hasChanges()).isFalse();
        assertThat(review.changes()).isEmpty();
    }

    @Test
    void pureReplanPositionChangeRendersAUserFacingOrderingDiff() {
        AiImprovementContent content = content("Titel", "Beschreibung", TaskPriority.MEDIUM, 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        UUID sectionId = UUID.randomUUID();
        AiReplanPlacementProposal placement = new AiReplanPlacementProposal(true,
                sectionId, "Section", "nach \"Element A\"", sectionId, "Section", "vor \"Element A\"",
                UUID.randomUUID(), null, SortMode.DATE);
        AiImprovementProposal proposal = new AiImprovementProposal(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AiImprovementElementType.TASK, 0, AiFeedbackType.REPLAN, null,
                "Die Aufgabe wird vor ihrem fachlichen Ergebnis eingeordnet.", placement, content, content);

        AiImprovementReview review = AiImprovementReview.from(proposal);

        assertThat(review.hasChanges()).isTrue();
        assertThat(review.changes()).hasSize(1);
        assertThat(review.changes().getFirst().key()).isEqualTo("ordering");
        assertThat(review.changes().getFirst().label()).isEqualTo("Reihenfolge");
        assertThat(review.changes().getFirst().originalValue()).isEqualTo("nach \"Element A\"");
        assertThat(review.changes().getFirst().proposedValue()).isEqualTo("vor \"Element A\"");
    }

    @Test
    void unchangedReplanPlacementDoesNotCreateAnOrderingDiff() {
        AiImprovementContent content = content("Titel", "Beschreibung", TaskPriority.MEDIUM, 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        UUID sectionId = UUID.randomUUID();
        AiReplanPlacementProposal placement = new AiReplanPlacementProposal(false,
                sectionId, "Section", "vor \"Element A\"", sectionId, "Section", "Unverändert",
                null, null, SortMode.DATE);
        AiImprovementProposal proposal = new AiImprovementProposal(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AiImprovementElementType.TASK, 0, AiFeedbackType.REPLAN, null,
                "Die bestehende Einordnung bleibt sinnvoll.", placement, content, content);

        AiImprovementReview review = AiImprovementReview.from(proposal);

        assertThat(review.hasChanges()).isFalse();
        assertThat(review.changes()).isEmpty();
    }

    private AiImprovementProposal proposal(
            AiFeedbackType feedbackType,
            AiImprovementContent original,
            AiImprovementContent proposed
    ) {
        return new AiImprovementProposal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AiImprovementElementType.TASK, 0, feedbackType, null, original, proposed);
    }

    private AiImprovementContent content(
            String title,
            String description,
            TaskPriority priority,
            Integer hours,
            LocalDate startDate,
            LocalDate dueDate
    ) {
        return new AiImprovementContent(AiImprovementElementType.TASK,
                title, description, priority, hours, startDate, dueDate);
    }
}

package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSortModeForm;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.model.*;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.user.model.User;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DraftOrderingIntegrationTest {
    @Autowired DraftReviewService reviews;
    @Autowired
    DraftRepository drafts;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;

    @Test
    void originTransitionsAreExplicitAndStable() {
        assertThat(ElementOrigin.AI.modifiedByUser()).isEqualTo(ElementOrigin.AI_MODIFIED);
        assertThat(ElementOrigin.AI_MODIFIED.modifiedByUser()).isEqualTo(ElementOrigin.AI_MODIFIED);
        assertThat(ElementOrigin.TEMPLATE.modifiedByUser()).isEqualTo(ElementOrigin.TEMPLATE_MODIFIED);
        assertThat(ElementOrigin.TEMPLATE_MODIFIED.modifiedByUser()).isEqualTo(ElementOrigin.TEMPLATE_MODIFIED);
        assertThat(ElementOrigin.USER.modifiedByUser()).isEqualTo(ElementOrigin.USER);
    }

    @Test
    void taskAndMilestoneShareManualOrderAndCanMoveBetweenSectionsWithoutChangingOrigin() {
        Fixture fixture = fixture();
        DraftElementMoveForm move = move(fixture.draft(), fixture.second(), 0);
        reviews.moveElement(fixture.project().getId(), fixture.milestone().getId(), fixture.owner().getId(), move);

        var review = reviews.review(fixture.project().getId(), fixture.owner().getId());
        assertThat(review.getSections().get(1).getElements()).extracting("title")
                .containsExactly("Meilenstein", "Zweite Aufgabe");
        assertThat(fixture.milestone().getOrigin()).isEqualTo(ElementOrigin.AI);
        assertThat(fixture.first().getElements()).extracting(DraftPlanElement::getSortOrder)
                .containsExactly(0);
        assertThat(fixture.second().getElements()).extracting(DraftPlanElement::getSortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void chronologicalViewUsesOnlyTaskDeadlineAndMilestoneDateAndManualOrderReturns() {
        Fixture fixture = fixture();
        fixture.task().setStartDate(LocalDate.of(2027, 12, 1));
        fixture.task().setDueDate(LocalDate.of(2027, 3, 10));
        fixture.milestone().setDueDate(LocalDate.of(2027, 2, 1));
        fixture.task().setSortOrder(0);
        fixture.milestone().setSortOrder(1);

        assertThat(reviews.review(fixture.project().getId(), fixture.owner().getId())
                .getSections().getFirst().getElements()).extracting("title")
                .containsExactly("Meilenstein", "Aufgabe");

        DraftSortModeForm manual = new DraftSortModeForm();
        manual.setLockVersion(fixture.draft().getLockVersion());
        manual.setSortMode(SortMode.MANUAL);
        reviews.updateSortMode(fixture.project().getId(), fixture.owner().getId(), manual);
        assertThat(fixture.task().getOrigin()).isEqualTo(ElementOrigin.AI);
        assertThat(fixture.milestone().getOrigin()).isEqualTo(ElementOrigin.AI);
        assertThat(reviews.review(fixture.project().getId(), fixture.owner().getId())
                .getSections().getFirst().getElements()).extracting("title")
                .containsExactly("Aufgabe", "Meilenstein");
    }

    @Test
    void datedElementCannotMoveInsideChronologicalSectionButCanCrossSections() {
        Fixture fixture = fixture();
        fixture.task().setDueDate(LocalDate.of(2027, 3, 10));
        assertThatThrownBy(() -> reviews.moveElement(fixture.project().getId(), fixture.task().getId(),
                fixture.owner().getId(), move(fixture.draft(), fixture.first(), 1)))
                .isInstanceOf(DomainValidationException.class);

        DraftElementMoveForm crossSection = move(fixture.draft(), fixture.second(), 1);
        crossSection.setLockVersion(reviews.review(fixture.project().getId(), fixture.owner().getId()).getLockVersion());
        reviews.moveElement(fixture.project().getId(), fixture.task().getId(), fixture.owner().getId(), crossSection);
        assertThat(fixture.task().getDraftSection()).isSameAs(fixture.second());
        assertThat(fixture.task().getOrigin()).isEqualTo(ElementOrigin.AI);
    }

    @Test
    void sectionsCanBeReorderedIndependentlyOfDateMode() {
        Fixture fixture = fixture();
        DraftSectionMoveForm move = new DraftSectionMoveForm();
        move.setLockVersion(fixture.draft().getLockVersion());
        move.setTargetPosition(0);
        reviews.moveSection(fixture.project().getId(), fixture.second().getId(), fixture.owner().getId(), move);
        assertThat(reviews.review(fixture.project().getId(), fixture.owner().getId()).getSections())
                .extracting("title").containsExactly("Zweiter Bereich", "Erster Bereich");
    }

    private DraftElementMoveForm move(DraftPlan draft, DraftSection target, int position) {
        DraftElementMoveForm form = new DraftElementMoveForm();
        form.setLockVersion(draft.getLockVersion());
        form.setTargetSectionId(target.getId());
        form.setTargetPosition(position);
        return form;
    }

    private Fixture fixture() {
        User owner = new User();
        owner.setEmail(java.util.UUID.randomUUID() + "@example.org"); owner.setDisplayName("Owner");
        owner.setPasswordHash("hash"); owner.setEnabled(true); users.save(owner);
        Project project = new Project(); project.setTitle("Draft"); project.setCreationType(CreationType.AI);
        project.setStatus(ProjectStatus.DRAFT); project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember(); membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER); membership.setActive(true); project.addMembership(membership);
        projects.save(project);
        DraftPlan draft = new DraftPlan(); project.attachDraft(draft);
        DraftSection first = section("Erster Bereich", 0); DraftSection second = section("Zweiter Bereich", 1);
        draft.addSection(first); draft.addSection(second);
        DraftTask task = task("Aufgabe", 0); DraftMilestone milestone = milestone("Meilenstein", 1);
        DraftTask secondTask = task("Zweite Aufgabe", 0);
        draft.addElement(task); first.addElement(task); draft.addElement(milestone); first.addElement(milestone);
        draft.addElement(secondTask); second.addElement(secondTask); drafts.saveAndFlush(draft);
        return new Fixture(owner, project, draft, first, second, task, milestone);
    }

    private DraftSection section(String title, int order) {
        DraftSection value = new DraftSection(); value.setTitle(title); value.setSortOrder(order); return value;
    }
    private DraftTask task(String title, int order) {
        DraftTask value = new DraftTask(); value.setTitle(title); value.setSortOrder(order); return value;
    }
    private DraftMilestone milestone(String title, int order) {
        DraftMilestone value = new DraftMilestone(); value.setTitle(title); value.setSortOrder(order); return value;
    }
    private record Fixture(User owner, Project project, DraftPlan draft, DraftSection first,
                           DraftSection second, DraftTask task, DraftMilestone milestone) {}
}

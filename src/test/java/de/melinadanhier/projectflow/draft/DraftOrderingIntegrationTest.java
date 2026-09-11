package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.draft.dto.editing.DraftElementMoveForm;
import de.melinadanhier.projectflow.draft.dto.editing.DraftSectionMoveForm;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftReviewService;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.classification.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.CreationType;
import de.melinadanhier.projectflow.plancontainer.project.model.lifecycle.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMemberRole;
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
        DraftElementMoveForm move = move(fixture.draft(), fixture.second(), 0, null);
        reviews.moveElement(fixture.project().getId(), fixture.milestone().getId(), fixture.owner().getId(), move);

        var review = reviews.review(fixture.project().getId(), fixture.owner().getId());
        assertThat(review.getSections().get(1).getElements()).extracting("title")
                .containsExactly("Meilenstein", "Zweite Aufgabe");
        assertThat(fixture.milestone().getOrigin()).isEqualTo(ElementOrigin.AI);
        assertThat(fixture.first().getElements()).extracting(DraftPlanElement::getSortOrder)
                .containsExactly(100);
        assertThat(fixture.milestone().getSortOrder()).isZero();
        assertThat(fixture.secondTask().getSortOrder()).isEqualTo(100);
    }

    @Test
    void dateOrderSortsDatedElementsButKeepsUndatedManualSlots() {
        Fixture fixture = fixture();
        fixture.task().setStartDate(LocalDate.of(2027, 12, 1));
        fixture.task().setDueDate(LocalDate.of(2027, 3, 10));
        fixture.milestone().setDueDate(LocalDate.of(2027, 2, 1));
        fixture.task().setSortOrder(100);
        DraftTask undated = task("Ohne Datum", 200);
        fixture.draft().addElement(undated);
        fixture.first().addElement(undated);
        fixture.milestone().setSortOrder(300);
        drafts.flush();

        assertThat(reviews.review(fixture.project().getId(), fixture.owner().getId())
                .getSections().getFirst().getElements()).extracting("title")
                .containsExactly("Meilenstein", "Ohne Datum", "Aufgabe");

        assertThat(fixture.task().getOrigin()).isEqualTo(ElementOrigin.AI);
        assertThat(fixture.milestone().getOrigin()).isEqualTo(ElementOrigin.AI);
    }

    @Test
    void datedElementCanMoveInsideChronologicalSectionAndAcrossSections() {
        Fixture fixture = fixture();
        fixture.task().setDueDate(LocalDate.of(2027, 3, 10));
        reviews.moveElement(fixture.project().getId(), fixture.task().getId(),
                fixture.owner().getId(), move(fixture.draft(), fixture.first(), 1, null));
        assertThat(fixture.task().getDraftSection()).isSameAs(fixture.first());

        DraftElementMoveForm crossSection = move(fixture.draft(), fixture.second(), 0,
                LocalDate.of(2027, 3, 10));
        crossSection.setLockVersion(reviews.review(fixture.project().getId(), fixture.owner().getId()).getLockVersion());
        reviews.moveElement(fixture.project().getId(), fixture.task().getId(), fixture.owner().getId(), crossSection);
        assertThat(fixture.task().getDraftSection()).isSameAs(fixture.second());
        assertThat(fixture.task().getOrigin()).isEqualTo(ElementOrigin.AI);
    }

    @Test
    void elementsWithTheSameDateCanBeReorderedSparsely() {
        Fixture fixture = fixture();
        LocalDate date = LocalDate.of(2027, 3, 10);
        fixture.task().setDueDate(date);
        fixture.milestone().setDueDate(date);
        reviews.moveElement(fixture.project().getId(), fixture.milestone().getId(), fixture.owner().getId(),
                move(fixture.draft(), fixture.first(), 0, date));
        assertThat(reviews.review(fixture.project().getId(), fixture.owner().getId())
                .getSections().getFirst().getElements()).extracting("title")
                .containsExactly("Meilenstein", "Aufgabe");
        assertThat(fixture.milestone().getSortOrder()).isLessThan(fixture.task().getSortOrder());
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

    private DraftElementMoveForm move(DraftPlan draft, DraftSection target, int position, LocalDate date) {
        DraftElementMoveForm form = new DraftElementMoveForm();
        form.setLockVersion(draft.getLockVersion());
        form.setTargetSectionId(target.getId());
        form.setTargetDate(date == null ? "" : date.toString());
        form.setTargetPosition(position);
        return form;
    }

    private Fixture fixture() {
        User owner = new User();
        owner.setEmail(java.util.UUID.randomUUID() + "@example.org"); owner.setDisplayName("Owner");
        owner.setPasswordHash("hash"); owner.setEnabled(true); users.save(owner);
        Project project = new Project(); project.setTitle("Draft"); project.setCreationType(CreationType.AI);
        project.setLocation(ProjectLocation.DRAFT);
        ProjectMember membership = new ProjectMember(); membership.setUser(owner);
        membership.setRole(ProjectMemberRole.OWNER); membership.setActive(true); project.addMembership(membership);
        projects.save(project);
        DraftPlan draft = new DraftPlan(); project.attachDraft(draft);
        DraftSection first = section("Erster Bereich", 100); DraftSection second = section("Zweiter Bereich", 200);
        draft.addSection(first); draft.addSection(second);
        DraftTask task = task("Aufgabe", 100); DraftMilestone milestone = milestone("Meilenstein", 200);
        DraftTask secondTask = task("Zweite Aufgabe", 100);
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
                           DraftSection second, DraftTask task, DraftMilestone milestone) {
        DraftTask secondTask() { return (DraftTask) second.getElements().getFirst(); }
    }
}

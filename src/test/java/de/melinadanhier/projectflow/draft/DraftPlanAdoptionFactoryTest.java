package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.service.DraftPlanAdoptionFactory;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanReviewStatus;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftPlanAdoptionFactoryTest {

    @Test
    void adoptionPreservesSparseManualOrderAndKeepsProjectPreference() {
        DraftPlan draft = new DraftPlan();
        draft.setSortMode(SortMode.MANUAL);
        DraftSection section = new DraftSection();
        section.setTitle("Bereich");
        section.setSortOrder(350);
        section.setReviewStatus(DraftReviewStatus.PENDING);
        draft.addSection(section);
        DraftTask first = task("Erste", 175);
        first.setReviewStatus(DraftReviewStatus.PENDING);
        DraftTask second = task("Zweite", 625);
        second.setReviewStatus(DraftReviewStatus.ACCEPTED);
        draft.addElement(first); section.addElement(first);
        draft.addElement(second); section.addElement(second);

        Project project = new Project();
        project.setTitle("Projekt");
        project.setSortMode(SortMode.DATE);
        new DraftPlanAdoptionFactory().adopt(draft, project);

        assertThat(project.getSortMode()).isEqualTo(SortMode.DATE);
        assertThat(project.getSections()).singleElement()
                .extracting(PlanSection::getSortOrder).isEqualTo(350);
        assertThat(project.getSections()).singleElement()
                .extracting(PlanSection::getReviewStatus).isEqualTo(PlanReviewStatus.UNREVIEWED);
        assertThat(project.getElements()).extracting(PlanElement::getSortOrder)
                .containsExactly(175, 625);
        assertThat(project.getElements()).extracting(PlanElement::getReviewStatus)
                .containsExactly(PlanReviewStatus.UNREVIEWED, PlanReviewStatus.CONFIRMED);
    }

    private DraftTask task(String title, int order) {
        DraftTask task = new DraftTask();
        task.setTitle(title);
        task.setSortOrder(order);
        return task;
    }
}

package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.plancontainer.model.SortMode;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.service.PlanElementCollection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanElementCollectionTest {

    @Test
    void derivesTypedViewsAndDateDisplayFromOneSharedElementOrder() {
        PlanSection section = new PlanSection();
        setId(section);
        section.setSortOrder(10);
        LocalDate dueDate = LocalDate.of(2026, 10, 30);

        Milestone milestone = milestone(section, dueDate, 10);
        Task laterTask = task(section, dueDate.plusDays(1), 20);
        Task sameDateTask = task(section, dueDate, 30);
        PlanElementCollection elements = PlanElementCollection.copyOf(
                List.of(sameDateTask, laterTask, milestone));

        assertThat(elements.tasks()).containsExactly(laterTask, sameDateTask);
        assertThat(elements.milestones()).containsExactly(milestone);
        assertThat(elements.displayInSection(section.getId(), SortMode.MANUAL))
                .containsExactly(milestone, laterTask, sameDateTask);
        assertThat(elements.displayInSection(section.getId(), SortMode.DATE))
                .containsExactly(milestone, sameDateTask, laterTask);
        assertThat(elements.taskCount(section.getId())).isEqualTo(2);
        assertThat(elements.milestoneCount(section.getId())).isEqualTo(1);
    }

    private Task task(PlanSection section, LocalDate dueDate, int sortOrder) {
        Task task = new Task();
        setId(task);
        task.setPlanSection(section);
        task.setDueDate(dueDate);
        task.setSortOrder(sortOrder);
        return task;
    }

    private Milestone milestone(PlanSection section, LocalDate dueDate, int sortOrder) {
        Milestone milestone = new Milestone();
        setId(milestone);
        milestone.setPlanSection(section);
        milestone.setDueDate(dueDate);
        milestone.setSortOrder(sortOrder);
        return milestone;
    }

    private void setId(Object entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    }
}

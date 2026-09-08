package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.planelement.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDependencyPolicyTest {

    @Test
    void acceptsEqualDatesAndIncompleteDatePairs() {
        Task prerequisite = task("Vorbereitung");
        prerequisite.setDueDate(LocalDate.of(2026, 10, 10));
        Task sameDaySuccessor = task("Umsetzung");
        sameDaySuccessor.setStartDate(LocalDate.of(2026, 10, 10));
        Task undatedSuccessor = task("Ohne Termin");

        assertThat(TaskDependencyPolicy.temporalWarning(prerequisite, sameDaySuccessor)).isEmpty();
        assertThat(TaskDependencyPolicy.temporalWarning(prerequisite, undatedSuccessor)).isEmpty();
    }

    @Test
    void comparesDueDatesWhenTheSuccessorHasNoStartDate() {
        Task prerequisite = task("Vorbereitung");
        prerequisite.setDueDate(LocalDate.of(2026, 10, 10));
        Task successor = task("Umsetzung");
        successor.setDueDate(LocalDate.of(2026, 10, 9));

        assertThat(TaskDependencyPolicy.temporalWarning(prerequisite, successor)).isPresent();
    }

    @Test
    void comparesStartDatesOnlyWhenThePrerequisiteHasNoDueDate() {
        Task prerequisite = task("Vorbereitung");
        prerequisite.setStartDate(LocalDate.of(2026, 10, 10));
        Task successor = task("Umsetzung");
        successor.setStartDate(LocalDate.of(2026, 10, 9));

        assertThat(TaskDependencyPolicy.temporalWarning(prerequisite, successor)).isPresent();
    }

    private Task task(String title) {
        Task task = new Task();
        task.setTitle(title);
        return task;
    }
}

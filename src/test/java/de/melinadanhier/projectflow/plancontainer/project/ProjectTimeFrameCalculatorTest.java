package de.melinadanhier.projectflow.plancontainer.project;

import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectBasicsForm;
import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectTimeFrameType;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectTimeFrameCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTimeFrameCalculatorTest {

    private final ProjectTimeFrameCalculator calculator = new ProjectTimeFrameCalculator();

    @Test
    void calculatesTheEndDateWithInclusiveDuration() {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(3);

        ProjectTimeFrameCalculator.ProjectTimeFrame result = calculator.calculate(form);

        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void calculatesTheStartDateWithInclusiveDuration() {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTimeFrameType(ProjectTimeFrameType.END_AND_DURATION);
        form.setEndDate(LocalDate.of(2026, 9, 3));
        form.setDurationDays(3);

        ProjectTimeFrameCalculator.ProjectTimeFrame result = calculator.calculate(form);

        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void oneDayDurationUsesTheSameStartAndEndDate() {
        ProjectBasicsForm form = new ProjectBasicsForm();
        form.setTimeFrameType(ProjectTimeFrameType.START_AND_DURATION);
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setDurationDays(1);

        ProjectTimeFrameCalculator.ProjectTimeFrame result = calculator.calculate(form);

        assertThat(result.endDate()).isEqualTo(result.startDate());
    }
}

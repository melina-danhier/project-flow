package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.plancontainer.project.dto.ProjectBasicsForm;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ProjectTimeFrameCalculator {

    /**
     * Die Dauer zählt beide Grenztage mit: Ein Projekt mit Dauer 1 beginnt
     * und endet am selben Kalendertag.
     */
    public ProjectTimeFrame calculate(ProjectBasicsForm form) {
        return switch (form.getTimeFrameType()) {
            case START_AND_END -> new ProjectTimeFrame(form.getStartDate(), form.getEndDate());
            case START_AND_DURATION -> new ProjectTimeFrame(
                    form.getStartDate(),
                    form.getStartDate().plusDays(form.getDurationDays() - 1L)
            );
            case END_AND_DURATION -> new ProjectTimeFrame(
                    form.getEndDate().minusDays(form.getDurationDays() - 1L),
                    form.getEndDate()
            );
            case NONE -> new ProjectTimeFrame(null, null);
        };
    }

    public record ProjectTimeFrame(LocalDate startDate, LocalDate endDate) {
    }
}

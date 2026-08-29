package de.melinadanhier.projectflow.ai.validation.generation;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.time.LocalDate;
import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;

/** Prüft Elementtermine gegen den bestätigten Projektzeitraum. */
final class GenerationDateValidator {

    private final List<GenerationValidationIssue> issues;
    private final LocalDate projectStart;
    private final LocalDate projectEnd;
    private final boolean datesRequired;

    GenerationDateValidator(AiWizardSnapshot snapshot, List<GenerationValidationIssue> issues) {
        this.issues = issues;
        projectStart = snapshot == null ? null : snapshot.startDate();
        projectEnd = snapshot == null ? null : snapshot.endDate();
        datesRequired = snapshot != null && (snapshot.timeFrameType() != null
                ? snapshot.timeFrameType() != AiProjectTimeFrameType.NONE
                : projectStart != null || projectEnd != null);
    }

    void validateTask(GeneratedTask task, String taskPath) {
        if (datesRequired && task.dueDate() == null) {
            addIssue(TASK_DUE_DATE_MISSING, taskPath + ".dueDate");
        }
        dateOrder(
                TASK_DATES_INVALID,
                task.startDate(), task.dueDate()
        );
        inProjectRange(
                TASK_DATE_OUTSIDE_PROJECT,
                task.startDate()
        );
        inProjectRange(
                TASK_DATE_OUTSIDE_PROJECT,
                task.dueDate()
        );
    }

    void validateMilestone(GeneratedMilestone milestone, String milestonePath) {
        if (datesRequired && milestone.date() == null) {
            addIssue(MILESTONE_DATE_MISSING, milestonePath + ".date");
        }
        inProjectRange(
                MILESTONE_DATE_OUTSIDE_PROJECT,
                milestone.date()
        );
    }

    private void dateOrder(GenerationValidationCode code, LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            addIssue(code);
        }
    }

    private void inProjectRange(GenerationValidationCode code, LocalDate date) {
        if (outsideRange(date, projectStart, projectEnd)) {
            addIssue(code);
        }
    }

    private boolean outsideRange(LocalDate date, LocalDate start, LocalDate end) {
        return date != null &&
                ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end)));
    }

    private void addIssue(GenerationValidationCode code, String path) {
        issues.add(new GenerationValidationIssue(code, path));
    }

    private void addIssue(GenerationValidationCode code) {
        issues.add(new GenerationValidationIssue(code));
    }
}

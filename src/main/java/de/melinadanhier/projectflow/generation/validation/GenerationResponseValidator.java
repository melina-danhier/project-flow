package de.melinadanhier.projectflow.generation.validation;

import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedMilestone;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPhase;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedTask;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GenerationResponseValidator {

    private final Validator beanValidator;

    public GenerationResponseValidator(Validator beanValidator) {
        this.beanValidator = beanValidator;
    }

    public GenerationValidationResult validate(
            GeneratedPlanResponse response,
            AiGenerationRequest request
    ) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        if (response == null) {
            add(issues, "RESPONSE_MISSING", "Es wurde kein Projektplan erzeugt.");
            return GenerationValidationResult.of(issues);
        }
        beanValidator.validate(response).stream()
                .map(violation -> new GenerationValidationIssue(
                        "BEAN_VALIDATION_FAILED",
                        violation.getPropertyPath() + ": " + violation.getMessage()))
                .forEach(issues::add);
        if (response.metadata() == null || blank(response.metadata().summary())) {
            add(issues, "SUMMARY_MISSING", "Die Zusammenfassung des Projektplans fehlt.");
        }
        if (response.metadata() != null && response.metadata().assumptions() == null) {
            add(issues, "ASSUMPTIONS_MISSING", "Die Annahmen-Liste fehlt.");
        }
        if (response.phases() == null || response.phases().isEmpty()) {
            add(issues, "PHASE_MISSING", "Es wurde keine Phase erzeugt.");
            add(issues, "TASK_MISSING", "Es wurde keine Aufgabe erzeugt.");
            return GenerationValidationResult.of(issues);
        }

        int taskCount = 0;
        Set<String> tempIds = new HashSet<>();
        LocalDate projectStart = request.confirmedWizardData().startDate();
        LocalDate projectEnd = request.confirmedWizardData().endDate();

        for (GeneratedPhase phase : response.phases()) {
            if (phase == null) {
                add(issues, "PHASE_INVALID", "Der Plan enthält eine leere Phase.");
                continue;
            }
            requiredText(issues, "PHASE_TITLE_MISSING", "Ein Phasentitel fehlt.", phase.title());
            positive(issues, "PHASE_ORDER_INVALID", "Eine Phasenreihenfolge ist nicht positiv.", phase.order());
            unique(issues, tempIds, phase.tempId());
            paired(issues, "PHASE_DATES_INCOMPLETE", "Eine Phase benötigt Start und Ende oder keine Termine.",
                    phase.startDate(), phase.endDate());
            ordered(issues, "PHASE_DATES_INVALID", "Eine Phase beginnt nach ihrem Ende.",
                    phase.startDate(), phase.endDate());
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT", "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.startDate(), projectStart, projectEnd);
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT", "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.endDate(), projectStart, projectEnd);

            List<GeneratedTask> tasks = phase.tasks();
            if (tasks == null) {
                add(issues, "TASKS_MISSING", "Bei einer Phase fehlt die Aufgaben-Liste.");
            } else {
                taskCount += tasks.size();
                for (GeneratedTask task : tasks) {
                    if (task == null) {
                        add(issues, "TASK_INVALID", "Der Plan enthält eine leere Aufgabe.");
                        continue;
                    }
                    requiredText(issues, "TASK_TITLE_MISSING", "Ein Aufgabentitel fehlt.", task.title());
                    positive(issues, "TASK_ORDER_INVALID", "Eine Aufgabenreihenfolge ist nicht positiv.", task.order());
                    unique(issues, tempIds, task.tempId());
                    if (task.origin() == null) {
                        add(issues, "TASK_ORIGIN_MISSING", "Bei einer Aufgabe fehlt die Herkunft.");
                    }
                    if (task.estimatedHours() != null && task.estimatedHours() <= 0) {
                        add(issues, "TASK_EFFORT_INVALID", "Ein Aufgabenaufwand muss größer als 0 sein.");
                    }
                    if (task.startDate() != null && task.dueDate() == null) {
                        add(issues, "TASK_DATES_INCOMPLETE",
                                "Eine Aufgabe mit Startdatum benötigt auch ein Fälligkeitsdatum.");
                    }
                    ordered(issues, "TASK_DATES_INVALID", "Eine Aufgabe beginnt nach ihrem Fälligkeitsdatum.",
                            task.startDate(), task.dueDate());
                    inProjectRange(issues, "TASK_DATE_OUTSIDE_PROJECT", "Ein Aufgabentermin liegt außerhalb des Projektzeitraums.",
                            task.startDate(), projectStart, projectEnd);
                    inProjectRange(issues, "TASK_DATE_OUTSIDE_PROJECT", "Ein Aufgabentermin liegt außerhalb des Projektzeitraums.",
                            task.dueDate(), projectStart, projectEnd);
                    inPhaseRange(issues, task.startDate(), phase.startDate(), phase.endDate());
                    inPhaseRange(issues, task.dueDate(), phase.startDate(), phase.endDate());
                }
            }

            List<GeneratedMilestone> milestones = phase.milestones();
            if (milestones == null) {
                add(issues, "MILESTONES_MISSING", "Bei einer Phase fehlt die Meilenstein-Liste.");
            } else {
                for (GeneratedMilestone milestone : milestones) {
                    if (milestone == null) {
                        add(issues, "MILESTONE_INVALID", "Der Plan enthält einen leeren Meilenstein.");
                        continue;
                    }
                    requiredText(issues, "MILESTONE_TITLE_MISSING", "Ein Meilensteintitel fehlt.", milestone.title());
                    positive(issues, "MILESTONE_ORDER_INVALID", "Eine Meilensteinreihenfolge ist nicht positiv.", milestone.order());
                    unique(issues, tempIds, milestone.tempId());
                    inProjectRange(issues, "MILESTONE_DATE_OUTSIDE_PROJECT", "Ein Meilensteintermin liegt außerhalb des Projektzeitraums.",
                            milestone.date(), projectStart, projectEnd);
                    inPhaseRange(issues, milestone.date(), phase.startDate(), phase.endDate());
                }
            }
        }
        if (taskCount == 0) {
            add(issues, "TASK_MISSING", "Es wurde keine Aufgabe erzeugt.");
        }
        return GenerationValidationResult.of(issues);
    }

    private void unique(List<GenerationValidationIssue> issues, Set<String> ids, String id) {
        if (blank(id)) {
            add(issues, "TEMP_ID_MISSING", "Bei einem Planelement fehlt die temporäre ID.");
        } else if (!ids.add(id)) {
            add(issues, "TEMP_ID_DUPLICATE", "Die temporäre ID '" + id + "' wird mehrfach verwendet.");
        }
    }

    private void ordered(List<GenerationValidationIssue> issues, String code, String message,
                         LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            add(issues, code, message);
        }
    }

    private void inProjectRange(List<GenerationValidationIssue> issues, String code, String message,
                                LocalDate date, LocalDate projectStart, LocalDate projectEnd) {
        if (date != null && ((projectStart != null && date.isBefore(projectStart))
                || (projectEnd != null && date.isAfter(projectEnd)))) {
            add(issues, code, message);
        }
    }

    private void requiredText(List<GenerationValidationIssue> issues, String code, String message, String value) {
        if (blank(value)) {
            add(issues, code, message);
        }
    }

    private void positive(List<GenerationValidationIssue> issues, String code, String message, int value) {
        if (value <= 0) {
            add(issues, code, message);
        }
    }

    private void paired(List<GenerationValidationIssue> issues, String code, String message,
                        LocalDate start, LocalDate end) {
        if ((start == null) != (end == null)) {
            add(issues, code, message);
        }
    }

    private void inPhaseRange(List<GenerationValidationIssue> issues, LocalDate date,
                              LocalDate phaseStart, LocalDate phaseEnd) {
        if (date != null && ((phaseStart != null && date.isBefore(phaseStart))
                || (phaseEnd != null && date.isAfter(phaseEnd)))) {
            add(issues, "ELEMENT_DATE_OUTSIDE_PHASE",
                    "Ein Elementtermin liegt außerhalb des zugehörigen Phasenzeitraums.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void add(List<GenerationValidationIssue> issues, String code, String message) {
        issues.add(new GenerationValidationIssue(code, message));
    }
}

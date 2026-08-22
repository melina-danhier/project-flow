package de.melinadanhier.projectflow.ai.validation.generation;

import de.melinadanhier.projectflow.generation.model.wizard.AiProjectTimeFrameType;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPhase;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

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
            return new GenerationValidationResult(issues);
        }

        beanValidator.validate(response).stream()
                .map(violation -> new GenerationValidationIssue(
                        "BEAN_VALIDATION_FAILED",
                        violation.getPropertyPath() + ": " + violation.getMessage()))
                .forEach(issues::add);

        AiWizardSnapshot snapshot = wizardSnapshot(request, issues);
        LocalDate projectStart = snapshot == null ? null : snapshot.startDate();
        LocalDate projectEnd = snapshot == null ? null : snapshot.endDate();
        Boolean planUsesDates = snapshot == null ? null : planUsesDates(snapshot);

        if (response.metadata() == null || blank(response.metadata().summary())) {
            add(issues, "SUMMARY_MISSING", "Die Zusammenfassung des Projektplans fehlt.");
        }
        if (response.metadata() != null && response.metadata().assumptions() == null) {
            add(issues, "ASSUMPTIONS_MISSING", "Die Annahmen-Liste fehlt.");
        }
        if (response.phases() == null || response.phases().isEmpty()) {
            add(issues, "PHASE_MISSING", "Es wurde keine Phase erzeugt.");
            add(issues, "TASK_MISSING", "Es wurde keine Aufgabe erzeugt.");
            return new GenerationValidationResult(issues);
        }

        int taskCount = 0;
        Set<String> tempIds = new HashSet<>();
        Set<Integer> phaseOrders = new HashSet<>();

        for (GeneratedPhase phase : response.phases()) {
            if (phase == null) {
                add(issues, "PHASE_INVALID", "Der Plan enthält eine leere Phase.");
                continue;
            }
            requiredText(issues, "PHASE_TITLE_MISSING", "Ein Phasentitel fehlt.", phase.title());
            positiveOrder(issues, "PHASE_ORDER_INVALID", "Eine Phasenreihenfolge ist nicht positiv.",
                    "PHASE_ORDER_DUPLICATE", "Eine Phasenreihenfolge wird mehrfach verwendet.",
                    phase.order(), phaseOrders);
            uniqueTempId(issues, tempIds, phase.tempId());
            paired(issues, "PHASE_DATES_INCOMPLETE",
                    "Eine Phase benötigt Start und Ende oder keine Termine.",
                    phase.startDate(), phase.endDate());
            ordered(issues, "PHASE_DATES_INVALID", "Eine Phase beginnt nach ihrem Ende.",
                    phase.startDate(), phase.endDate());
            validateDatePresence(issues, planUsesDates, phase.startDate(), phase.endDate(),
                    "Eine Phase ist entgegen der Projektzeitplanung nicht konsistent terminiert.");
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT",
                    "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.startDate(), projectStart, projectEnd);
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT",
                    "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.endDate(), projectStart, projectEnd);

            List<GeneratedTask> tasks = phase.tasks();
            if (tasks == null) {
                add(issues, "TASKS_MISSING", "Bei einer Phase fehlt die Aufgaben-Liste.");
            } else {
                taskCount += tasks.size();
                validateTasks(issues, tasks, phase, tempIds, planUsesDates, projectStart, projectEnd);
            }

            List<GeneratedMilestone> milestones = phase.milestones();
            if (milestones == null) {
                add(issues, "MILESTONES_MISSING", "Bei einer Phase fehlt die Meilenstein-Liste.");
            } else {
                validateMilestones(issues, milestones, phase, tempIds, planUsesDates,
                        projectStart, projectEnd);
            }
        }
        if (taskCount == 0) {
            add(issues, "TASK_MISSING", "Es wurde keine Aufgabe erzeugt.");
        }
        return new GenerationValidationResult(issues);
    }

    private void validateTasks(
            List<GenerationValidationIssue> issues,
            List<GeneratedTask> tasks,
            GeneratedPhase phase,
            Set<String> tempIds,
            Boolean planUsesDates,
            LocalDate projectStart,
            LocalDate projectEnd
    ) {
        Set<Integer> taskOrders = new HashSet<>();
        for (GeneratedTask task : tasks) {
            if (task == null) {
                add(issues, "TASK_INVALID", "Der Plan enthält eine leere Aufgabe.");
                continue;
            }
            requiredText(issues, "TASK_TITLE_MISSING", "Ein Aufgabentitel fehlt.", task.title());
            positiveOrder(issues, "TASK_ORDER_INVALID", "Eine Aufgabenreihenfolge ist nicht positiv.",
                    "TASK_ORDER_DUPLICATE", "Eine Aufgabenreihenfolge wird innerhalb der Phase mehrfach verwendet.",
                    task.order(), taskOrders);
            uniqueTempId(issues, tempIds, task.tempId());
            if (task.origin() == null) {
                add(issues, "TASK_ORIGIN_MISSING", "Bei einer Aufgabe fehlt die Herkunft.");
            }
            if (task.estimatedHours() != null && task.estimatedHours() <= 0) {
                add(issues, "TASK_EFFORT_INVALID", "Ein Aufgabenaufwand muss größer als 0 sein.");
            }
            paired(issues, "TASK_DATES_INCOMPLETE",
                    "Eine Aufgabe benötigt Start- und Fälligkeitsdatum oder keine Termine.",
                    task.startDate(), task.dueDate());
            ordered(issues, "TASK_DATES_INVALID", "Eine Aufgabe beginnt nach ihrem Fälligkeitsdatum.",
                    task.startDate(), task.dueDate());
            validateDatePresence(issues, planUsesDates, task.startDate(), task.dueDate(),
                    "Eine Aufgabe ist entgegen der Projektzeitplanung nicht konsistent terminiert.");
            inProjectRange(issues, "TASK_DATE_OUTSIDE_PROJECT",
                    "Ein Aufgabentermin liegt außerhalb des Projektzeitraums.",
                    task.startDate(), projectStart, projectEnd);
            inProjectRange(issues, "TASK_DATE_OUTSIDE_PROJECT",
                    "Ein Aufgabentermin liegt außerhalb des Projektzeitraums.",
                    task.dueDate(), projectStart, projectEnd);
            inPhaseRange(issues, task.startDate(), phase.startDate(), phase.endDate());
            inPhaseRange(issues, task.dueDate(), phase.startDate(), phase.endDate());
        }
    }

    private void validateMilestones(
            List<GenerationValidationIssue> issues,
            List<GeneratedMilestone> milestones,
            GeneratedPhase phase,
            Set<String> tempIds,
            Boolean planUsesDates,
            LocalDate projectStart,
            LocalDate projectEnd
    ) {
        Set<Integer> milestoneOrders = new HashSet<>();
        for (GeneratedMilestone milestone : milestones) {
            if (milestone == null) {
                add(issues, "MILESTONE_INVALID", "Der Plan enthält einen leeren Meilenstein.");
                continue;
            }
            requiredText(issues, "MILESTONE_TITLE_MISSING", "Ein Meilensteintitel fehlt.", milestone.title());
            positiveOrder(issues, "MILESTONE_ORDER_INVALID",
                    "Eine Meilensteinreihenfolge ist nicht positiv.",
                    "MILESTONE_ORDER_DUPLICATE",
                    "Eine Meilensteinreihenfolge wird innerhalb der Phase mehrfach verwendet.",
                    milestone.order(), milestoneOrders);
            uniqueTempId(issues, tempIds, milestone.tempId());
            validateDatePresence(issues, planUsesDates, milestone.date(),
                    "Ein Meilenstein ist entgegen der Projektzeitplanung nicht konsistent terminiert.");
            inProjectRange(issues, "MILESTONE_DATE_OUTSIDE_PROJECT",
                    "Ein Meilensteintermin liegt außerhalb des Projektzeitraums.",
                    milestone.date(), projectStart, projectEnd);
            inPhaseRange(issues, milestone.date(), phase.startDate(), phase.endDate());
        }
    }

    private AiWizardSnapshot wizardSnapshot(
            AiGenerationRequest request,
            List<GenerationValidationIssue> issues
    ) {
        if (request == null) {
            add(issues, "REQUEST_MISSING", "Die zugehörige Generierungsanfrage fehlt.");
            return null;
        }
        if (request.confirmedWizardData() == null) {
            add(issues, "WIZARD_DATA_MISSING", "Die bestätigten Wizard-Daten fehlen.");
            return null;
        }
        return request.confirmedWizardData();
    }

    private boolean planUsesDates(AiWizardSnapshot snapshot) {
        AiProjectTimeFrameType type = snapshot.timeFrameType();
        if (type != null) {
            return type != AiProjectTimeFrameType.NONE;
        }
        return snapshot.startDate() != null || snapshot.endDate() != null;
    }

    private void validateDatePresence(
            List<GenerationValidationIssue> issues,
            Boolean planUsesDates,
            LocalDate start,
            LocalDate end,
            String message
    ) {
        if (planUsesDates == null) {
            return;
        }
        boolean hasAnyDate = start != null || end != null;
        boolean hasAllDates = start != null && end != null;
        if ((planUsesDates && !hasAllDates) || (!planUsesDates && hasAnyDate)) {
            add(issues, "DATE_PLANNING_INCONSISTENT", message);
        }
    }

    private void validateDatePresence(
            List<GenerationValidationIssue> issues,
            Boolean planUsesDates,
            LocalDate date,
            String message
    ) {
        if (planUsesDates != null && planUsesDates != (date != null)) {
            add(issues, "DATE_PLANNING_INCONSISTENT", message);
        }
    }

    private void uniqueTempId(List<GenerationValidationIssue> issues, Set<String> ids, String id) {
        if (blank(id)) {
            add(issues, "TEMP_ID_MISSING", "Bei einem Planelement fehlt die temporäre ID.");
        } else if (!ids.add(id)) {
            add(issues, "TEMP_ID_DUPLICATE", "Die temporäre ID '" + id + "' wird mehrfach verwendet.");
        }
    }

    private void positiveOrder(
            List<GenerationValidationIssue> issues,
            String invalidCode,
            String invalidMessage,
            String duplicateCode,
            String duplicateMessage,
            int value,
            Set<Integer> values
    ) {
        if (value <= 0) {
            add(issues, invalidCode, invalidMessage);
        } else if (!values.add(value)) {
            add(issues, duplicateCode, duplicateMessage);
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

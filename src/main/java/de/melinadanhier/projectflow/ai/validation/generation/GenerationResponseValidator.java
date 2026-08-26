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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;

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
                        violation.getPropertyPath().toString(),
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
        if (response.phases().size() > MAX_PHASES) {
            add(issues, "PHASE_LIMIT_EXCEEDED", "phases",
                    "Der Plan enthält mehr als " + MAX_PHASES + " Phasen.");
        }

        int taskCount = 0;
        int milestoneCount = 0;
        int dependencyCount = 0;
        Set<String> taskTempIds = new HashSet<>();
        Map<String, GeneratedTask> tasksById = new HashMap<>();
        Map<String, String> taskPathsById = new HashMap<>();
        Set<Integer> phaseOrders = new HashSet<>();

        for (int phaseIndex = 0; phaseIndex < response.phases().size(); phaseIndex++) {
            GeneratedPhase phase = response.phases().get(phaseIndex);
            String phasePath = "phases[" + phaseIndex + "]";
            if (phase == null) {
                add(issues, "PHASE_INVALID", phasePath, "Der Plan enthält eine leere Phase.");
                continue;
            }
            requiredText(issues, "PHASE_TITLE_MISSING", "Ein Phasentitel fehlt.", phase.title());
            optionalText(issues, "PHASE_DESCRIPTION_BLANK", phasePath + ".description",
                    "Eine vorhandene Phasenbeschreibung darf nicht leer sein.", phase.description());
            positiveOrder(issues, "PHASE_ORDER_INVALID", "Eine Phasenreihenfolge ist nicht positiv.",
                    "PHASE_ORDER_DUPLICATE", "Eine Phasenreihenfolge wird mehrfach verwendet.",
                    phase.order(), phaseOrders);
            ordered(issues, "PHASE_DATES_INVALID", "Eine Phase beginnt nach ihrem Ende.",
                    phase.startDate(), phase.endDate());
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT",
                    "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.startDate(), projectStart, projectEnd);
            inProjectRange(issues, "PHASE_DATE_OUTSIDE_PROJECT",
                    "Ein Phasentermin liegt außerhalb des Projektzeitraums.",
                    phase.endDate(), projectStart, projectEnd);

            List<GeneratedTask> tasks = phase.tasks();
            if (tasks == null) {
                add(issues, "TASKS_MISSING", "Bei einer Phase fehlt die Aufgaben-Liste.");
            } else if (tasks.isEmpty()) {
                add(issues, "PHASE_TASK_MISSING", phasePath + ".tasks",
                        "Jede Phase muss mindestens eine Aufgabe enthalten.");
            } else {
                taskCount += tasks.size();
                dependencyCount += tasks.stream().filter(java.util.Objects::nonNull)
                        .map(GeneratedTask::prerequisiteTaskTempIds)
                        .filter(java.util.Objects::nonNull).mapToInt(List::size).sum();
                validateTasks(issues, tasks, phase, taskTempIds, tasksById, taskPathsById,
                        phasePath, planUsesDates, projectStart, projectEnd);
            }

            List<GeneratedMilestone> milestones = phase.milestones();
            if (milestones == null) {
                add(issues, "MILESTONES_MISSING", "Bei einer Phase fehlt die Meilenstein-Liste.");
            } else {
                milestoneCount += milestones.size();
                validateMilestones(issues, milestones, phase, planUsesDates,
                        projectStart, projectEnd, phasePath);
            }
        }
        if (taskCount < MIN_TASKS) {
            if (taskCount == 0) {
                add(issues, "TASK_MISSING", "phases", "Es wurde keine Aufgabe erzeugt.");
            }
            add(issues, "TASK_COUNT_TOO_LOW", "phases",
                    "Der Plan muss mindestens " + MIN_TASKS + " Aufgaben enthalten.");
        }
        if (taskCount > MAX_TASKS) {
            add(issues, "TASK_LIMIT_EXCEEDED", "phases",
                    "Der Plan enthält mehr als " + MAX_TASKS + " Aufgaben.");
        }
        if (milestoneCount > MAX_MILESTONES) {
            add(issues, "MILESTONE_LIMIT_EXCEEDED", "phases",
                    "Der Plan enthält mehr als " + MAX_MILESTONES + " Meilensteine.");
        }
        if (dependencyCount > MAX_DEPENDENCIES) {
            add(issues, "DEPENDENCY_LIMIT_EXCEEDED", "phases",
                    "Der Plan enthält mehr als " + MAX_DEPENDENCIES + " Abhängigkeiten.");
        }
        validateDependencies(issues, tasksById, taskPathsById);
        return new GenerationValidationResult(issues);
    }

    private void validateTasks(
            List<GenerationValidationIssue> issues,
            List<GeneratedTask> tasks,
            GeneratedPhase phase,
            Set<String> taskTempIds,
            Map<String, GeneratedTask> tasksById,
            Map<String, String> taskPathsById,
            String phasePath,
            Boolean planUsesDates,
            LocalDate projectStart,
            LocalDate projectEnd
    ) {
        Set<Integer> taskOrders = new HashSet<>();
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            GeneratedTask task = tasks.get(taskIndex);
            String taskPath = phasePath + ".tasks[" + taskIndex + "]";
            if (task == null) {
                add(issues, "TASK_INVALID", taskPath, "Der Plan enthält eine leere Aufgabe.");
                continue;
            }
            requiredText(issues, "TASK_TITLE_MISSING", "Ein Aufgabentitel fehlt.", task.title());
            optionalText(issues, "TASK_DESCRIPTION_BLANK", taskPath + ".description",
                    "Eine vorhandene Aufgabenbeschreibung darf nicht leer sein.", task.description());
            positiveOrder(issues, "TASK_ORDER_INVALID", "Eine Aufgabenreihenfolge ist nicht positiv.",
                    "TASK_ORDER_DUPLICATE", "Eine Aufgabenreihenfolge wird innerhalb der Phase mehrfach verwendet.",
                    task.order(), taskOrders);
            uniqueTempId(issues, taskTempIds, task.tempId(), taskPath + ".tempId");
            if (!blank(task.tempId())) {
                tasksById.putIfAbsent(task.tempId(), task);
                taskPathsById.putIfAbsent(task.tempId(), taskPath);
            }
            if (task.origin() == null) {
                add(issues, "TASK_ORIGIN_MISSING", "Bei einer Aufgabe fehlt die Herkunft.");
            }
            if (task.estimatedHours() != null
                    && (task.estimatedHours() <= 0 || task.estimatedHours() > MAX_ESTIMATED_HOURS)) {
                add(issues, "TASK_EFFORT_INVALID", taskPath + ".estimatedHours",
                        "Ein Aufgabenaufwand muss zwischen 1 und " + MAX_ESTIMATED_HOURS + " Stunden liegen.");
            }
            if (Boolean.TRUE.equals(planUsesDates) && task.dueDate() == null) {
                add(issues, "TASK_DUE_DATE_MISSING", taskPath + ".dueDate",
                        "Bei terminierter Planung benötigt jede Aufgabe ein Fälligkeitsdatum.");
            }
            ordered(issues, "TASK_DATES_INVALID", "Eine Aufgabe beginnt nach ihrem Fälligkeitsdatum.",
                    task.startDate(), task.dueDate());
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
            Boolean planUsesDates,
            LocalDate projectStart,
            LocalDate projectEnd,
            String phasePath
    ) {
        Set<Integer> milestoneOrders = new HashSet<>();
        for (int milestoneIndex = 0; milestoneIndex < milestones.size(); milestoneIndex++) {
            GeneratedMilestone milestone = milestones.get(milestoneIndex);
            String milestonePath = phasePath + ".milestones[" + milestoneIndex + "]";
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
            if (Boolean.TRUE.equals(planUsesDates) && milestone.date() == null) {
                add(issues, "MILESTONE_DATE_MISSING", milestonePath + ".date",
                        "Bei terminierter Planung benötigt jeder Meilenstein ein Datum.");
            }
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

    private void uniqueTempId(List<GenerationValidationIssue> issues, Set<String> ids, String id,
                              String path) {
        if (blank(id)) {
            add(issues, "TEMP_ID_MISSING", path, "Bei einem Planelement fehlt die temporäre ID.");
        } else if (!ids.add(id)) {
            add(issues, "TEMP_ID_DUPLICATE", path, "Die temporäre ID wird mehrfach verwendet.");
        }
    }

    private void validateDependencies(List<GenerationValidationIssue> issues,
                                      Map<String, GeneratedTask> tasksById,
                                      Map<String, String> taskPathsById) {
        for (var entry : tasksById.entrySet()) {
            String taskId = entry.getKey();
            GeneratedTask task = entry.getValue();
            String path = taskPathsById.get(taskId) + ".prerequisiteTaskTempIds";
            if (task.prerequisiteTaskTempIds() == null) {
                add(issues, "DEPENDENCIES_MISSING", path, "Die Abhängigkeitsliste fehlt.");
                continue;
            }
            Set<String> seenPrerequisites = new HashSet<>();
            for (int index = 0; index < task.prerequisiteTaskTempIds().size(); index++) {
                String prerequisite = task.prerequisiteTaskTempIds().get(index);
                String referencePath = path + "[" + index + "]";
                if (blank(prerequisite)) {
                    continue;
                }
                if (!seenPrerequisites.add(prerequisite)) {
                    add(issues, "DEPENDENCY_DUPLICATE", referencePath,
                            "Dieselbe gerichtete Abhängigkeit ist mehrfach angegeben.");
                } else if (taskId.equals(prerequisite)) {
                    add(issues, "SELF_DEPENDENCY", referencePath,
                            "Eine Aufgabe darf nicht von sich selbst abhängen.");
                } else if (!tasksById.containsKey(prerequisite)) {
                    add(issues, "UNKNOWN_TASK_REFERENCE", referencePath,
                            "Die Abhängigkeit verweist auf keine vorhandene Aufgabe.");
                } else {
                    validateDependencyDates(issues, tasksById.get(prerequisite), task, referencePath);
                }
            }
        }
        detectCycles(issues, tasksById, taskPathsById);
    }

    private void validateDependencyDates(List<GenerationValidationIssue> issues,
                                         GeneratedTask prerequisite,
                                         GeneratedTask successor,
                                         String path) {
        if (successor.startDate() != null && prerequisite.dueDate() != null
                && successor.startDate().isBefore(prerequisite.dueDate())) {
            add(issues, "DEPENDENCY_DATE_ORDER_INVALID", path,
                    "Die Aufgabe beginnt vor der Deadline ihrer Voraussetzung.");
        } else if (successor.startDate() == null && successor.dueDate() != null
                && prerequisite.dueDate() != null
                && successor.dueDate().isBefore(prerequisite.dueDate())) {
            add(issues, "DEPENDENCY_DATE_ORDER_INVALID", path,
                    "Die Aufgabe endet vor der Deadline ihrer Voraussetzung.");
        }
    }

    private void detectCycles(List<GenerationValidationIssue> issues,
                              Map<String, GeneratedTask> tasksById,
                              Map<String, String> taskPathsById) {
        Set<String> completed = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String taskId : tasksById.keySet()) {
            if (hasCycle(taskId, tasksById, completed, active)) {
                add(issues, "DEPENDENCY_CYCLE", taskPathsById.get(taskId) + ".prerequisiteTaskTempIds",
                        "Die Aufgabenabhängigkeiten enthalten einen Zyklus.");
                return;
            }
        }
    }

    private boolean hasCycle(String taskId, Map<String, GeneratedTask> tasksById,
                             Set<String> completed, Set<String> active) {
        if (active.contains(taskId)) return true;
        if (completed.contains(taskId)) return false;
        active.add(taskId);
        GeneratedTask task = tasksById.get(taskId);
        if (task != null && task.prerequisiteTaskTempIds() != null) {
            for (String prerequisite : task.prerequisiteTaskTempIds()) {
                if (!taskId.equals(prerequisite) && tasksById.containsKey(prerequisite)
                        && hasCycle(prerequisite, tasksById, completed, active)) return true;
            }
        }
        active.remove(taskId);
        completed.add(taskId);
        return false;
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

    private void optionalText(List<GenerationValidationIssue> issues, String code, String path,
                              String message, String value) {
        if (value != null && value.isBlank()) {
            add(issues, code, path, message);
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
        add(issues, code, "$", message);
    }

    private void add(List<GenerationValidationIssue> issues, String code, String path, String message) {
        issues.add(new GenerationValidationIssue(code, path, message));
    }
}

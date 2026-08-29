package de.melinadanhier.projectflow.ai.validation.generation;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;

/** Sammelt Aufgaben eines Prüflaufs und prüft anschließend ihre Abhängigkeiten. */
final class GenerationDependencyValidator {

    private final List<GenerationValidationIssue> issues;
    private final Map<String, TaskReference> tasksById = new HashMap<>();

    GenerationDependencyValidator(List<GenerationValidationIssue> issues) {
        this.issues = issues;
    }

    void register(GeneratedTask task, String path) {
        if (task.tempId() == null || task.tempId().isBlank()) {
            addIssue(TEMP_ID_MISSING, path + ".tempId");
        } else if (tasksById.putIfAbsent(task.tempId(), new TaskReference(task, path)) != null) {
            addIssue(TEMP_ID_DUPLICATE, path + ".tempId");
        }
    }

    void validate() {
        for (var entry : tasksById.entrySet()) {
            validateReferences(entry.getKey(), entry.getValue());
        }
        detectCycles();
    }

    private void validateReferences(String taskId, TaskReference reference) {
        GeneratedTask task = reference.task();
        String path = reference.path() + ".prerequisiteTaskTempIds";
        if (task.prerequisiteTaskTempIds() == null) {
            addIssue(DEPENDENCIES_MISSING, path);
            return;
        }
        Set<String> seenPrerequisites = new HashSet<>();
        for (int index = 0; index < task.prerequisiteTaskTempIds().size(); index++) {
            String prerequisite = task.prerequisiteTaskTempIds().get(index);
            String referencePath = path + "[" + index + "]";
            if (prerequisite == null || prerequisite.isBlank()) {
                continue;
            }
            if (!seenPrerequisites.add(prerequisite)) {
                addIssue(DEPENDENCY_DUPLICATE, referencePath);
            } else if (taskId.equals(prerequisite)) {
                addIssue(SELF_DEPENDENCY, referencePath);
            } else if (!tasksById.containsKey(prerequisite)) {
                addIssue(UNKNOWN_TASK_REFERENCE, referencePath);
            } else {
                validateDependencyDates(tasksById.get(prerequisite).task(), task, referencePath);
            }
        }
    }

    private void validateDependencyDates(GeneratedTask prerequisite, GeneratedTask successor, String path) {
        if (successor.startDate() != null && prerequisite.dueDate() != null
                && successor.startDate().isBefore(prerequisite.dueDate())) {
            addIssue(DEPENDENCY_DATE_ORDER_INVALID, path);
        } else if (successor.startDate() == null && successor.dueDate() != null
                && prerequisite.dueDate() != null
                && successor.dueDate().isBefore(prerequisite.dueDate())) {
            addIssue(DEPENDENCY_DATE_ORDER_INVALID, path);
        }
    }

    private void detectCycles() {
        Set<String> completed = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String taskId : tasksById.keySet()) {
            if (hasCycle(taskId, completed, active)) {
                addIssue(DEPENDENCY_CYCLE, tasksById.get(taskId).path() + ".prerequisiteTaskTempIds");
                return;
            }
        }
    }

    private boolean hasCycle(String taskId, Set<String> completed, Set<String> active) {
        if (active.contains(taskId)) return true;
        if (completed.contains(taskId)) return false;
        active.add(taskId);
        GeneratedTask task = tasksById.get(taskId).task();
        if (task.prerequisiteTaskTempIds() != null) {
            for (String prerequisite : task.prerequisiteTaskTempIds()) {
                if (!taskId.equals(prerequisite) && tasksById.containsKey(prerequisite)
                        && hasCycle(prerequisite, completed, active)) return true;
            }
        }
        active.remove(taskId);
        completed.add(taskId);
        return false;
    }

    private void addIssue(GenerationValidationCode code, String path) {
        issues.add(new GenerationValidationIssue(code, path));
    }

    private record TaskReference(GeneratedTask task, String path) { }
}

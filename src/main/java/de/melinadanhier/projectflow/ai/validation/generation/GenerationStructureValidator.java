package de.melinadanhier.projectflow.ai.validation.generation;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedSection;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.*;
import static de.melinadanhier.projectflow.ai.validation.generation.GenerationValidationCode.*;

/** Prüft Inhalte, Reihenfolgen und Mengengrenzen eines einzelnen Plans. */
final class GenerationStructureValidator {

    private final List<GenerationValidationIssue> issues;
    private final GenerationDateValidator dates;
    private final GenerationDependencyValidator dependencies;
    private int taskCount;
    private int milestoneCount;
    private int dependencyCount;

    GenerationStructureValidator(AiWizardSnapshot snapshot, List<GenerationValidationIssue> issues) {
        this.issues = issues;
        dates = new GenerationDateValidator(snapshot, issues);
        dependencies = new GenerationDependencyValidator(issues);
    }

    void validate(GeneratedPlanResponse response) {
        if (response.sections() == null || response.sections().isEmpty()) {
            addIssue(SECTION_MISSING);
            addIssue(TASK_MISSING);
            return;
        }
        if (response.sections().size() > MAX_SECTIONS) {
            addIssue(SECTION_LIMIT_EXCEEDED, "sections");
        }

        Set<Integer> sectionOrders = new HashSet<>();
        for (int index = 0; index < response.sections().size(); index++) {
            validateSection(response.sections().get(index), "sections[" + index + "]", sectionOrders);
        }
        validateTotals();
        dependencies.validate();
    }

    private void validateSection(GeneratedSection section, String path, Set<Integer> orders) {
        if (section == null) {
            addIssue(SECTION_INVALID, path);
            return;
        }
        requiredText(SECTION_TITLE_MISSING, section.title());
        optionalText(SECTION_DESCRIPTION_BLANK, path + ".description", section.description());
        positiveOrder(SECTION_ORDER_INVALID, SECTION_ORDER_DUPLICATE, section.order(), orders);
        validateTasks(section, path);
        validateMilestones(section, path);
    }

    private void validateTasks(GeneratedSection section, String sectionPath) {
        List<GeneratedTask> tasks = section.tasks();
        if (tasks == null) {
            addIssue(TASKS_MISSING);
            return;
        }
        if (tasks.isEmpty()) {
            addIssue(SECTION_TASK_MISSING, sectionPath + ".tasks");
            return;
        }

        taskCount += tasks.size();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < tasks.size(); index++) {
            GeneratedTask task = tasks.get(index);
            String path = sectionPath + ".tasks[" + index + "]";
            if (task == null) {
                addIssue(TASK_INVALID, path);
                continue;
            }
            if (task.prerequisiteTaskTempIds() != null) {
                dependencyCount += task.prerequisiteTaskTempIds().size();
            }
            validateTask(task, path, orders);
        }
    }

    private void validateTask(GeneratedTask task, String path, Set<Integer> orders) {
        requiredText(TASK_TITLE_MISSING, task.title());
        optionalText(TASK_DESCRIPTION_BLANK, path + ".description", task.description());
        positiveOrder(TASK_ORDER_INVALID, TASK_ORDER_DUPLICATE, task.order(), orders);
        dependencies.register(task, path);
        if (task.origin() == null) {
            addIssue(TASK_ORIGIN_MISSING);
        }
        if (task.estimatedHours() != null &&
                (task.estimatedHours() <= 0 || task.estimatedHours() > MAX_ESTIMATED_HOURS)) {
            addIssue(TASK_EFFORT_INVALID, path + ".estimatedHours");
        }
        dates.validateTask(task, path);
    }

    private void validateMilestones(GeneratedSection section, String sectionPath) {
        List<GeneratedMilestone> milestones = section.milestones();
        if (milestones == null) {
            addIssue(MILESTONES_MISSING);
            return;
        }
        milestoneCount += milestones.size();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < milestones.size(); index++) {
            GeneratedMilestone milestone = milestones.get(index);
            String path = sectionPath + ".milestones[" + index + "]";
            if (milestone == null) {
                addIssue(MILESTONE_INVALID);
                continue;
            }
            requiredText(MILESTONE_TITLE_MISSING, milestone.title());
            positiveOrder(MILESTONE_ORDER_INVALID, MILESTONE_ORDER_DUPLICATE, milestone.order(), orders);
            dates.validateMilestone(milestone, path);
        }
    }

    private void validateTotals() {
        if (taskCount < MIN_TASKS) {
            if (taskCount == 0) {
                addIssue(TASK_MISSING, "sections");
            }
            addIssue(TASK_COUNT_TOO_LOW, "sections");
        }
        if (taskCount > MAX_TASKS) {
            addIssue(TASK_LIMIT_EXCEEDED, "sections");
        }
        if (milestoneCount > MAX_MILESTONES) {
            addIssue(MILESTONE_LIMIT_EXCEEDED, "sections");
        }
        if (dependencyCount > MAX_DEPENDENCIES) {
            addIssue(DEPENDENCY_LIMIT_EXCEEDED, "sections");
        }
    }

    private void positiveOrder(GenerationValidationCode invalidCode,
                               GenerationValidationCode duplicateCode,
                               int value, Set<Integer> values) {
        if (value <= 0) {
            addIssue(invalidCode);
        } else if (!values.add(value)) {
            addIssue(duplicateCode);
        }
    }

    private void requiredText(GenerationValidationCode code, String value) {
        if (value == null || value.isBlank()) {
            addIssue(code);
        }
    }

    private void optionalText(GenerationValidationCode code, String path, String value) {
        if (value != null && value.isBlank()) {
            addIssue(code, path);
        }
    }

    private void addIssue(GenerationValidationCode code, String path) {
        issues.add(new GenerationValidationIssue(code, path));
    }

    private void addIssue(GenerationValidationCode code) {
        issues.add(new GenerationValidationIssue(code));
    }
}

package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TaskDependencyPolicy {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TaskDependencyPolicy() {
    }

    public static boolean isBlocked(Task task) {
        return task.getStatus() != TaskStatus.COMPLETED
                && task.getPrerequisites().stream()
                .anyMatch(prerequisite -> prerequisite.getStatus() != TaskStatus.COMPLETED);
    }

    public static List<String> temporalWarnings(Task task, List<Task> successors) {
        List<String> warnings = new ArrayList<>();
        task.getPrerequisites().stream()
                .map(prerequisite -> temporalWarning(prerequisite, task))
                .flatMap(Optional::stream)
                .forEach(warnings::add);
        successors.stream()
                .map(successor -> temporalWarning(task, successor))
                .flatMap(Optional::stream)
                .forEach(warnings::add);
        return List.copyOf(warnings);
    }

    static Optional<String> temporalWarning(Task prerequisite, Task successor) {
        LocalDate prerequisiteDue = prerequisite.getDueDate();
        LocalDate successorStart = successor.getStartDate();
        if (prerequisiteDue != null && successorStart != null && prerequisiteDue.isAfter(successorStart)) {
            return Optional.of("Die Voraussetzung \"" + prerequisite.getTitle() + "\" ist erst am "
                    + format(prerequisiteDue) + " fällig, aber \"" + successor.getTitle()
                    + "\" beginnt bereits am " + format(successorStart) + ".");
        }

        LocalDate successorDue = successor.getDueDate();
        if (prerequisiteDue != null && successorStart == null && successorDue != null
                && prerequisiteDue.isAfter(successorDue)) {
            return Optional.of("Die Voraussetzung \"" + prerequisite.getTitle() + "\" ist erst am "
                    + format(prerequisiteDue) + " fällig, nach der Fälligkeit von \""
                    + successor.getTitle() + "\" am " + format(successorDue) + ".");
        }

        LocalDate prerequisiteStart = prerequisite.getStartDate();
        if (prerequisiteDue == null && prerequisiteStart != null && successorStart != null
                && prerequisiteStart.isAfter(successorStart)) {
            return Optional.of("Die Voraussetzung \"" + prerequisite.getTitle() + "\" beginnt erst am "
                    + format(prerequisiteStart) + ", aber \"" + successor.getTitle()
                    + "\" beginnt bereits am " + format(successorStart) + ".");
        }
        return Optional.empty();
    }

    private static String format(LocalDate date) {
        return date.format(DATE_FORMAT);
    }
}

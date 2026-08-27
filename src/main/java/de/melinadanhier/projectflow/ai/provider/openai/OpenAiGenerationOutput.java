package de.melinadanhier.projectflow.ai.provider.openai;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record OpenAiGenerationOutput(
        List<Phase> phases
) {
    public record Phase(
            Optional<String> tempId,
            String title,
            Optional<String> description,
            Optional<LocalDate> startDate,
            Optional<LocalDate> endDate,
            int order,
            List<Task> tasks,
            List<Milestone> milestones
    ) {
        public Phase(String tempId, String title, Optional<String> description,
                     Optional<LocalDate> startDate, Optional<LocalDate> endDate, int order,
                     List<Task> tasks, List<Milestone> milestones) {
            this(Optional.ofNullable(tempId), title, description, startDate, endDate,
                    order, tasks, milestones);
        }
    }

    public record Task(
            String tempId,
            String title,
            Optional<String> description,
            Optional<Integer> estimatedHours,
            Optional<LocalDate> startDate,
            Optional<LocalDate> dueDate,
            Optional<String> criticalAssumption,
            GeneratedElementOrigin origin,
            int order,
            List<String> prerequisiteTaskTempIds,
            Optional<String> priority
    ) {
        public Task(String tempId, String title, Optional<String> description,
                    Optional<Integer> estimatedHours, Optional<LocalDate> startDate,
                    Optional<LocalDate> dueDate, Optional<String> criticalAssumption,
                    GeneratedElementOrigin origin, int order) {
            this(tempId, title, description, estimatedHours, startDate, dueDate,
                    criticalAssumption, origin, order, List.of(), Optional.empty());
        }
    }

    public record Milestone(
            Optional<String> tempId,
            String title,
            Optional<LocalDate> date,
            int order
    ) {
        public Milestone(String tempId, String title, Optional<LocalDate> date, int order) {
            this(Optional.ofNullable(tempId), title, date, order);
        }
    }
}

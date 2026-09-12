package de.melinadanhier.projectflow.ai.provider.openai;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-DTO für die automatische Schema-Erzeugung des OpenAI-SDKs. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record OpenAiGenerationOutput(
        List<Section> sections
) {

    public record Section(
            Optional<String> tempId,
            String title,
            Optional<String> description,
            int order,
            List<Task> tasks,
            List<Milestone> milestones
    ) {
        public Section {
            tempId = emptyIfNull(tempId);
            description = emptyIfNull(description);
        }

        public Section(String tempId, String title, Optional<String> description, int order,
                     List<Task> tasks, List<Milestone> milestones) {
            this(Optional.ofNullable(tempId), title, description, order, tasks, milestones);
        }
    }

    public record Task(
            String tempId,
            String title,
            Optional<String> description,
            Optional<Integer> estimatedHours,
            Optional<LocalDate> startDate,
            Optional<LocalDate> dueDate,
            int order,
            List<String> prerequisiteTaskTempIds,
            Optional<String> priority
    ) {
        public Task {
            description = emptyIfNull(description);
            estimatedHours = emptyIfNull(estimatedHours);
            startDate = emptyIfNull(startDate);
            dueDate = emptyIfNull(dueDate);
            priority = emptyIfNull(priority);
        }

        public Task(String tempId, String title, Optional<String> description,
                    Optional<Integer> estimatedHours, Optional<LocalDate> startDate,
                    Optional<LocalDate> dueDate, int order) {
            this(tempId, title, description, estimatedHours, startDate, dueDate,
                    order, List.of(), Optional.empty());
        }
    }

    public record Milestone(
            Optional<String> tempId,
            String title,
            Optional<LocalDate> date,
            int order
    ) {
        public Milestone {
            tempId = emptyIfNull(tempId);
            date = emptyIfNull(date);
        }

        public Milestone(String tempId, String title, Optional<LocalDate> date, int order) {
            this(Optional.ofNullable(tempId), title, date, order);
        }
    }

    private static <T> Optional<T> emptyIfNull(Optional<T> value) {
        return Objects.requireNonNullElse(value, Optional.empty());
    }
}

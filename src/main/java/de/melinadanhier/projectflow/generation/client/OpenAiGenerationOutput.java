package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record OpenAiGenerationOutput(Metadata metadata, List<Phase> phases) {

    public record Metadata(String summary, List<String> assumptions) {
    }

    public record Phase(
            String tempId, String title, Optional<String> description,
            Optional<LocalDate> startDate, Optional<LocalDate> endDate, int order,
            List<Task> tasks, List<Milestone> milestones
    ) {
    }

    public record Task(
            String tempId, String title, Optional<String> description,
            Optional<Integer> estimatedHours, Optional<LocalDate> startDate,
            Optional<LocalDate> dueDate, Optional<String> criticalAssumption,
            GeneratedElementOrigin origin, int order
    ) {
    }

    public record Milestone(String tempId, String title, Optional<LocalDate> date, int order) {
    }
}

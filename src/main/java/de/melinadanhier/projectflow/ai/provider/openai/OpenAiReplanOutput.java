package de.melinadanhier.projectflow.ai.provider.openai;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Provider-DTOs mit expliziter Null-Semantik für die Schema-Erzeugung des OpenAI-SDKs. */
public final class OpenAiReplanOutput {

    private OpenAiReplanOutput() {
    }

    public record Task(
            Optional<LocalDate> startDate,
            Optional<LocalDate> dueDate,
            Placement placement,
            String explanation
    ) {
        public Task {
            startDate = emptyIfNull(startDate);
            dueDate = emptyIfNull(dueDate);
        }
    }

    public record Milestone(
            Optional<LocalDate> dueDate,
            Placement placement,
            String explanation
    ) {
        public Milestone {
            dueDate = emptyIfNull(dueDate);
        }
    }

    public record Placement(
            boolean changePlacement,
            Optional<String> targetSectionId,
            Optional<String> beforeElementId,
            Optional<String> afterElementId
    ) {
        public Placement {
            targetSectionId = emptyIfNull(targetSectionId);
            beforeElementId = emptyIfNull(beforeElementId);
            afterElementId = emptyIfNull(afterElementId);
        }
    }

    private static <T> Optional<T> emptyIfNull(Optional<T> value) {
        return Objects.requireNonNullElse(value, Optional.empty());
    }
}

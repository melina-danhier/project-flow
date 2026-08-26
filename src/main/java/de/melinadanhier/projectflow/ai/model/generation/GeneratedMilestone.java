package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_TITLE_LENGTH;

public record GeneratedMilestone(
        @Size(max = 100) String tempId,
        @NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
        LocalDate date,
        @Positive int order
) {
    public GeneratedMilestone {
        tempId = trim(tempId);
        title = trim(title);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

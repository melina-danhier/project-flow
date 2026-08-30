package de.melinadanhier.projectflow.ai.model.generation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_DESCRIPTION_LENGTH;

public record GeneratedCriticalAssumption(
        @NotBlank @Size(max = MAX_DESCRIPTION_LENGTH) String statement,
        boolean correctionRequiredIfRejected
) {
    public GeneratedCriticalAssumption {
        statement = statement == null ? null : statement.strip();
    }
}

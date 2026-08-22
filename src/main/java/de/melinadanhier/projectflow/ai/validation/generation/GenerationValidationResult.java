package de.melinadanhier.projectflow.ai.validation.generation;

import java.util.List;
import java.util.Objects;

public record GenerationValidationResult(List<GenerationValidationIssue> issues) {

    public GenerationValidationResult {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues darf nicht null sein"));
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}

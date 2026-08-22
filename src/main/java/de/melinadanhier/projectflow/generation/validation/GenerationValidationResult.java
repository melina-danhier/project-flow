package de.melinadanhier.projectflow.generation.validation;

import java.util.List;

public record GenerationValidationResult(boolean valid, List<GenerationValidationIssue> issues) {

    public GenerationValidationResult {
        issues = List.copyOf(issues);
        valid = issues.isEmpty();
    }

    public static GenerationValidationResult of(List<GenerationValidationIssue> issues) {
        return new GenerationValidationResult(issues.isEmpty(), issues);
    }
}

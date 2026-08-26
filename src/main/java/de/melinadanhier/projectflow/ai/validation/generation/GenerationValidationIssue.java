package de.melinadanhier.projectflow.ai.validation.generation;

public record GenerationValidationIssue(String code, String fieldPath, String message) {

    public GenerationValidationIssue(String code, String message) {
        this(code, "$", message);
    }
}

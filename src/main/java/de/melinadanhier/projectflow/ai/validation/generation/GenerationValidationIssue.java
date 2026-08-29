package de.melinadanhier.projectflow.ai.validation.generation;

import java.util.Objects;

public record GenerationValidationIssue(GenerationValidationCode code, String fieldPath, String message) {

    public GenerationValidationIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fieldPath, "fieldPath");
        Objects.requireNonNull(message, "message");
    }

    public GenerationValidationIssue(GenerationValidationCode code) {
        this(code, "$");
    }

    public GenerationValidationIssue(GenerationValidationCode code, String fieldPath) {
        this(code, fieldPath, code.getDefaultMessage());
    }
}

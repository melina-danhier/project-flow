package de.melinadanhier.projectflow.generation.dto.response;

import java.util.List;

public record AiPreCheckResult(boolean hasPlausibilityIssues, List<String> issues) {

    public AiPreCheckResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static AiPreCheckResult withoutIssues() {
        return new AiPreCheckResult(false, List.of());
    }
}

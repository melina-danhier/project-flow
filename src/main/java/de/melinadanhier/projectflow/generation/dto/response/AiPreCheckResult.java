package de.melinadanhier.projectflow.generation.dto.response;

import de.melinadanhier.projectflow.generation.prompt.AiSchemaVersions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AiPreCheckResult(
        @NotNull String schemaVersion,
        @NotNull List<@Valid AiPreCheckProblem> problems
) {

    public AiPreCheckResult {
        problems = problems == null ? null : List.copyOf(problems);
    }

    public static AiPreCheckResult withoutIssues() {
        return new AiPreCheckResult(AiSchemaVersions.PRE_CHECK, List.of());
    }

    public boolean hasPlausibilityIssues() {
        return problems != null && !problems.isEmpty();
    }

    public boolean hasWarnings() {
        return problems != null && problems.stream()
                .anyMatch(problem -> problem.severity() == AiPreCheckSeverity.WARNING);
    }

    public boolean hasErrors() {
        return problems != null && problems.stream()
                .anyMatch(problem -> problem.severity() == AiPreCheckSeverity.ERROR);
    }
}

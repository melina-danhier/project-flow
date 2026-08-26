package de.melinadanhier.projectflow.ai.model.precheck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_PRE_CHECK_PROBLEMS;

public record AiPreCheckResult(
        @NotNull @Size(max = MAX_PRE_CHECK_PROBLEMS) List<@Valid AiPreCheckProblem> problems
) {

    public AiPreCheckResult {
        problems = problems == null ? null : List.copyOf(problems);
    }

    public static AiPreCheckResult withoutIssues() {
        return new AiPreCheckResult(List.of());
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

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
        if (problems != null) {
            List<AiPreCheckProblem> copy = List.copyOf(problems);
            boolean hasErrors = copy.stream()
                    .anyMatch(problem -> problem.severity() == AiPreCheckSeverity.ERROR);
            // Fachliche Regel zentral am Provider-Ergebnis: Blocker verdrängen alle offenen Punkte.
            problems = hasErrors
                    ? copy.stream().filter(problem -> problem.severity() == AiPreCheckSeverity.ERROR).toList()
                    : copy;
        }
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

    public List<AiPreCheckProblem> openPoints() {
        return problems == null ? List.of() : problems.stream()
                .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                .toList();
    }

    public boolean hasErrors() {
        return problems != null && problems.stream()
                .anyMatch(problem -> problem.severity() == AiPreCheckSeverity.ERROR);
    }
}

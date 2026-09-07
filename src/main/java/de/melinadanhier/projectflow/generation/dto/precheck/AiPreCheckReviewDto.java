package de.melinadanhier.projectflow.generation.dto.precheck;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;

import java.util.List;
import java.util.UUID;

public record AiPreCheckReviewDto(
        UUID workflowId,
        UUID projectId,
        List<AiPreCheckProblemDto> problems
) {
    public AiPreCheckReviewDto {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public boolean hasErrors() {
        return problems.stream()
                .anyMatch(p -> p.severity() == AiPreCheckSeverity.ERROR);
    }

    public boolean hasOpenPoints() {
        return problems.stream()
                .anyMatch(problem -> problem.isOpenPoint() && !problem.accepted());
    }

    public boolean canGenerate() {
        return !hasErrors() && !hasOpenPoints();
    }
}

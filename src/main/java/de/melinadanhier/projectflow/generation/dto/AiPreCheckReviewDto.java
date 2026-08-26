package de.melinadanhier.projectflow.generation.dto;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;

import java.util.List;
import java.util.UUID;

public record AiPreCheckReviewDto(
        UUID workflowId,
        UUID projectId,
        List<AiPreCheckProblemDto> problems
) {
    public boolean hasErrors() {
        return problems.stream()
                .anyMatch(p -> p.severity() == AiPreCheckSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return problems.stream()
                .anyMatch(problem -> problem.isWarning() && !problem.acknowledged());
    }
}

package de.melinadanhier.projectflow.generation.dto.response;

import java.util.List;
import java.util.UUID;

public record AiPreCheckReviewDto(
        UUID workflowId,
        UUID projectId,
        List<AiPreCheckProblemView> problems
) {
    public boolean hasErrors() {
        return problems.stream().anyMatch(problem -> problem.severity() == AiPreCheckSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return problems.stream().anyMatch(AiPreCheckProblemView::isWarning);
    }
}

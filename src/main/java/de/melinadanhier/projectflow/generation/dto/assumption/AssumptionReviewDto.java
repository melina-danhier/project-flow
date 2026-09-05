package de.melinadanhier.projectflow.generation.dto.assumption;

import java.util.List;
import java.util.UUID;

public record AssumptionReviewDto(
        UUID workflowId,
        UUID projectId,
        List<CriticalAssumptionReviewDto> assumptions,
        String errorMessage
) {
    public AssumptionReviewDto {
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }
}

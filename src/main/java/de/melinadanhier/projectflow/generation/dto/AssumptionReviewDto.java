package de.melinadanhier.projectflow.generation.dto;

import java.util.List;
import java.util.UUID;

public record AssumptionReviewDto(
        UUID workflowId,
        UUID projectId,
        List<CriticalAssumptionReviewDto> assumptions,
        String errorMessage
) { }

package de.melinadanhier.projectflow.generation.dto;

import java.util.List;

public record AssumptionReviewRequest(List<AssumptionDecisionRequest> decisions) {
    public AssumptionReviewRequest {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}

package de.melinadanhier.projectflow.generation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static de.melinadanhier.projectflow.ai.validation.AiResponseLimits.MAX_CRITICAL_ASSUMPTIONS;

@Getter
@Setter
@NoArgsConstructor
public class AssumptionReviewForm {

    @Size(max = MAX_CRITICAL_ASSUMPTIONS)
    private List<@NotNull @Valid DecisionForm> decisions = new ArrayList<>();

    public AssumptionReviewRequest toRequest() {
        return new AssumptionReviewRequest(decisions.stream()
                .map(decision -> new AssumptionDecisionRequest(
                        decision.getAssumptionIndex(), decision.getDecision(), decision.getCorrection()))
                .toList());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DecisionForm {

        @NotNull
        @Min(0)
        @Max(MAX_CRITICAL_ASSUMPTIONS - 1)
        private Integer assumptionIndex;

        @NotNull
        private AssumptionDecision decision;

        @Size(max = 2000)
        private String correction;
    }
}

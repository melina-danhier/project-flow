package de.melinadanhier.projectflow.generation.model.workflow;

import de.melinadanhier.projectflow.ai.model.generation.RejectedCriticalAssumption;

import java.util.List;

public record GenerationAssumptionContext(
        List<String> confirmedAssumptions,
        List<RejectedCriticalAssumption> rejectedAssumptions
) {
    public GenerationAssumptionContext {
        confirmedAssumptions = confirmedAssumptions == null ? List.of() : List.copyOf(confirmedAssumptions);
        rejectedAssumptions = rejectedAssumptions == null ? List.of() : List.copyOf(rejectedAssumptions);
    }

    public static GenerationAssumptionContext empty() {
        return new GenerationAssumptionContext(List.of(), List.of());
    }
}

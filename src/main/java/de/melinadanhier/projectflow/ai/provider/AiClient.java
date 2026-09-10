package de.melinadanhier.projectflow.ai.provider;

import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeRequest;
import de.melinadanhier.projectflow.ai.model.planchange.AiPlanChangeResponse;

/** Anbieterneutraler Vertrag für Pre-Check und Plangenerierung. */
public interface AiClient {

    AiPreCheckResult preCheck(AiPreCheckRequest request);

    GeneratedPlanResponse generatePlan(AiGenerationRequest request);

    AiImprovementResponse improveElement(AiImprovementRequest request);

    AiPlanChangeResponse proposePlanChanges(AiPlanChangeRequest request);

}

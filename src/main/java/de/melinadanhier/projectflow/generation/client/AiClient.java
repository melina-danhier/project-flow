package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;

/** Anbieterneutraler Vertrag für Pre-Check und Plangenerierung. */
public interface AiClient {

    AiPreCheckResult preCheck(AiPreCheckRequest request);

    GeneratedPlanResponse generatePlan(AiGenerationRequest request);
}

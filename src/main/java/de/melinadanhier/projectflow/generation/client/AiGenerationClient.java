package de.melinadanhier.projectflow.generation.client;

import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;

public interface AiGenerationClient {

    AiPreCheckResult preCheck(AiWizardSnapshot snapshot);
}

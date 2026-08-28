package de.melinadanhier.projectflow.ai.provider.gemini;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.AbstractProviderAiClient;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

public class GeminiAiClient extends AbstractProviderAiClient<GeneratedPlanResponse> {

    public GeminiAiClient(
            AiResponsesGateway gateway,
            GeminiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder
    ) {
        super(
                "gemini",
                gateway,
                properties::getPreCheckModel,
                properties::getGenerationModel,
                GeneratedPlanResponse.class,
                preCheckPromptBuilder,
                generationPromptBuilder
        );
    }

    @Override
    protected GeneratedPlanResponse mapPlan(GeneratedPlanResponse output) {
        return output;
    }
}

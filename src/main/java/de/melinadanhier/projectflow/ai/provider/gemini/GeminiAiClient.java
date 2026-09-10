package de.melinadanhier.projectflow.ai.provider.gemini;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.ImprovementPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PlanChangePromptBuilder;
import de.melinadanhier.projectflow.ai.provider.AbstractProviderAiClient;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

public class GeminiAiClient extends AbstractProviderAiClient<GeneratedPlanResponse> {

    public GeminiAiClient(
            AiResponsesGateway gateway,
            GeminiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder
    ) {
        this(gateway, properties, preCheckPromptBuilder, generationPromptBuilder,
                new ImprovementPromptBuilder(new tools.jackson.databind.ObjectMapper()),
                new PlanChangePromptBuilder(new tools.jackson.databind.ObjectMapper()));
    }

    public GeminiAiClient(
            AiResponsesGateway gateway,
            GeminiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder,
            ImprovementPromptBuilder improvementPromptBuilder
    ) {
        this(gateway, properties, preCheckPromptBuilder, generationPromptBuilder, improvementPromptBuilder,
                new PlanChangePromptBuilder(new tools.jackson.databind.ObjectMapper()));
    }

    public GeminiAiClient(
            AiResponsesGateway gateway,
            GeminiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder,
            ImprovementPromptBuilder improvementPromptBuilder,
            PlanChangePromptBuilder planChangePromptBuilder
    ) {
        super(
                "gemini",
                gateway,
                properties::getPreCheckModel,
                properties::getGenerationModel,
                GeneratedPlanResponse.class,
                preCheckPromptBuilder,
                generationPromptBuilder,
                improvementPromptBuilder,
                planChangePromptBuilder
        );
    }

    @Override
    protected GeneratedPlanResponse mapPlan(GeneratedPlanResponse output) {
        return output;
    }
}

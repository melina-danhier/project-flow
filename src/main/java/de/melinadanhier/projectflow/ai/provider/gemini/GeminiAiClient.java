package de.melinadanhier.projectflow.ai.provider.gemini;

import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GeminiAiClient implements AiClient {
    private final GeminiResponsesGateway gateway;
    private final GeminiProperties properties;
    private final PreCheckPromptBuilder preCheckPromptBuilder;
    private final GenerationPromptBuilder generationPromptBuilder;

    @Override
    public AiPreCheckResult preCheck(AiPreCheckRequest request) {
        return invoke(
                properties.getPreCheckModel(),
                preCheckPromptBuilder.build(request),
                AiSchemaVersions.PRE_CHECK,
                AiPreCheckResult.class
        );
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        return invoke(
                properties.getGenerationModel(),
                generationPromptBuilder.build(request),
                AiSchemaVersions.GENERATING_PLAN,
                GeneratedPlanResponse.class
        );
    }

    private <T> T invoke(String model, AiPrompt prompt, String schemaVersion, Class<T> responseType) {
        long startedAt = System.nanoTime();
        try {
            T result = gateway.execute(model, prompt, responseType);
            log.info("KI-Aufruf provider=gemini model={} promptVersion={} schemaVersion={} type={} durationMs={} result=success",
                    model, prompt.version(), schemaVersion, responseType.getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        } catch (AiTechnicalException exception) {
            log.warn("KI-Aufruf provider=gemini model={} promptVersion={} schemaVersion={} type={} durationMs={} errorCode={}",
                    model, prompt.version(), schemaVersion, responseType.getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000, exception.getErrorCode());
            throw exception;
        }
    }
}

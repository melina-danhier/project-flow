package de.melinadanhier.projectflow.ai.provider;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/** Gemeinsamer Aufrufablauf; der Provider bestimmt Ausgabetyp und Mapping der Plangenerierung. */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractProviderAiClient<T> implements AiClient {

    private final String provider;
    private final AiResponsesGateway gateway;
    private final Supplier<String> preCheckModel;
    private final Supplier<String> generationModel;
    private final Class<T> generationResponseType;
    private final PreCheckPromptBuilder preCheckPromptBuilder;
    private final GenerationPromptBuilder generationPromptBuilder;

    @Override
    public final AiPreCheckResult preCheck(AiPreCheckRequest request) {
        AiPrompt prompt = preCheckPromptBuilder.build(request);
        String model = preCheckModel.get();
        return invoke(AiOperation.PRE_CHECK, model, prompt,
                () -> gateway.execute(model, prompt, AiPreCheckResult.class));
    }

    @Override
    public final GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        AiPrompt prompt = generationPromptBuilder.build(request);
        String model = generationModel.get();
        return invoke(AiOperation.PLAN_GENERATION, model, prompt,
                () -> mapPlan(requireOutput(gateway.execute(model, prompt, generationResponseType))));
    }

    protected abstract GeneratedPlanResponse mapPlan(T output);

    protected final <R> R requireOutput(R output) {
        if (output == null)
            throw new AiOutputValidationException("Der KI-Anbieter lieferte einen leeren Ausgabewert.");
        return output;
    }

    private <R> R invoke(AiOperation operation, String model, AiPrompt prompt, Supplier<R> invocation) {
        String schemaVersion = schemaVersion(operation);
        long startedAt = System.nanoTime();
        try {
            R result = requireOutput(invocation.get());
            log.info("KI-Aufruf provider={} model={} promptVersion={} schemaVersion={} type={} durationMs={} result=success",
                    provider, model, prompt.version(), schemaVersion, operation, elapsedMillis(startedAt));
            return result;
        } catch (AiTechnicalException exception) {
            log.warn("KI-Aufruf provider={} model={} promptVersion={} schemaVersion={} type={} durationMs={} errorCode={}",
                    provider, model, prompt.version(), schemaVersion, operation, elapsedMillis(startedAt),
                    exception.getErrorCode());
            throw exception;
        }
    }

    private String schemaVersion(AiOperation operation) {
        return switch (operation) {
            case PRE_CHECK -> AiSchemaVersions.PRE_CHECK;
            case PLAN_GENERATION -> AiSchemaVersions.GENERATING_PLAN;
        };
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}

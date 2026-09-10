package de.melinadanhier.projectflow.ai.provider;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTextImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskReplanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiMilestoneReplanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskEffortResponse;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.ImprovementPromptBuilder;
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
    private final ImprovementPromptBuilder improvementPromptBuilder;

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

    @Override
    public final AiImprovementResponse improveElement(AiImprovementRequest request) {
        AiPrompt prompt = improvementPromptBuilder.build(request);
        String model = generationModel.get();
        return invoke("ELEMENT_IMPROVEMENT", AiSchemaVersions.ELEMENT_IMPROVEMENT, model, prompt,
                () -> executeImprovement(request, model, prompt));
    }

    private AiImprovementResponse executeImprovement(AiImprovementRequest request, String model, AiPrompt prompt) {
        var original = request.element();
        return switch (request.feedbackType()) {
            case IMPROVE, EXPAND, SIMPLIFY -> {
                AiTextImprovementResponse output = requireOutput(
                        gateway.execute(model, prompt, AiTextImprovementResponse.class));
                yield new AiImprovementResponse(original.elementType(), output.title(), output.description(),
                        original.priority(), original.estimatedHours(), original.startDate(), original.dueDate(), null);
            }
            case REPLAN -> switch (original.elementType()) {
                case TASK -> {
                    AiTaskReplanResponse output = requestTaskReplan(model, prompt);
                    yield new AiImprovementResponse(original.elementType(), original.title(), original.description(),
                            original.priority(), original.estimatedHours(), output.startDate(), output.dueDate(),
                            normalizePlacement(output.placement()), output.explanation());
                }
                case MILESTONE -> {
                    AiMilestoneReplanResponse output = requestMilestoneReplan(model, prompt);
                    yield new AiImprovementResponse(original.elementType(), original.title(), original.description(),
                            null, null, null, output.dueDate(), normalizePlacement(output.placement()),
                            output.explanation());
                }
                case SECTION -> throw new IllegalArgumentException("REPLAN ist für Sections nicht verfügbar.");
            };
            case ESTIMATE_EFFORT -> {
                if (original.elementType() != de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType.TASK) {
                    throw new IllegalArgumentException("ESTIMATE_EFFORT ist nur für Tasks verfügbar.");
                }
                AiTaskEffortResponse output = requireOutput(
                        gateway.execute(model, prompt, AiTaskEffortResponse.class));
                yield new AiImprovementResponse(original.elementType(), original.title(), original.description(),
                        original.priority(), output.estimatedHours(), original.startDate(), original.dueDate(),
                        output.explanation());
            }
        };
    }

    protected AiTaskReplanResponse requestTaskReplan(String model, AiPrompt prompt) {
        return executeStructured(model, prompt, AiTaskReplanResponse.class);
    }

    protected AiMilestoneReplanResponse requestMilestoneReplan(String model, AiPrompt prompt) {
        return executeStructured(model, prompt, AiMilestoneReplanResponse.class);
    }

    protected final <R> R executeStructured(String model, AiPrompt prompt, Class<R> responseType) {
        return requireOutput(gateway.execute(model, prompt, responseType));
    }

    private AiReplanPlacementResponse normalizePlacement(AiReplanPlacementResponse placement) {
        requireOutput(placement);
        return placement.changePlacement() ? placement : AiReplanPlacementResponse.unchanged();
    }

    protected abstract GeneratedPlanResponse mapPlan(T output);

    protected final <R> R requireOutput(R output) {
        if (output == null)
            throw new AiOutputValidationException("Der KI-Anbieter lieferte einen leeren Ausgabewert.");
        return output;
    }

    private <R> R invoke(AiOperation operation, String model, AiPrompt prompt, Supplier<R> invocation) {
        return invoke(operation.name(), schemaVersion(operation), model, prompt, invocation);
    }

    private <R> R invoke(
            String operation, String schemaVersion, String model, AiPrompt prompt, Supplier<R> invocation) {
        long startedAt = System.nanoTime();
        try {
            R result = requireOutput(invocation.get());
            log.info("KI-Aufruf provider={} model={} promptVersion={} schemaVersion={} type={} durationMs={} result=success",
                    provider, model, prompt.version(), schemaVersion, operation,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        } catch (AiTechnicalException exception) {
            log.warn("KI-Aufruf provider={} model={} promptVersion={} schemaVersion={} type={} durationMs={} errorCode={}",
                    provider, model, prompt.version(), schemaVersion, operation,
                    (System.nanoTime() - startedAt) / 1_000_000,
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
}

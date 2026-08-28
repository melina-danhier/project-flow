package de.melinadanhier.projectflow.ai.provider.openai;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.AiSchemaVersions;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;

@Slf4j
public class OpenAiProjectFlowAIClient implements AiClient {

    private final OpenAiResponsesGateway gateway;
    private final OpenAiProperties properties;
    private final PreCheckPromptBuilder preCheckPromptBuilder;
    private final GenerationPromptBuilder generationPromptBuilder;

    public OpenAiProjectFlowAIClient(
            OpenAiResponsesGateway gateway,
            OpenAiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder
    ) {
        this.gateway = gateway;
        this.properties = properties;
        this.preCheckPromptBuilder = preCheckPromptBuilder;
        this.generationPromptBuilder = generationPromptBuilder;
    }

    @Override
    public AiPreCheckResult preCheck(AiPreCheckRequest request) {
        var prompt = preCheckPromptBuilder.build(request);
        return invoke(
                AiOperation.PRE_CHECK, properties.getPreCheckModel(), prompt.version(),
                () -> requireOutput(gateway.execute(properties.getPreCheckModel(), prompt, AiPreCheckResult.class)));
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        var prompt = generationPromptBuilder.build(request);
        return invoke(
                AiOperation.PLAN_GENERATION, properties.getGenerationModel(), prompt.version(),
                () -> map(requireOutput(gateway.execute(properties.getGenerationModel(), prompt, OpenAiGenerationOutput.class))));
    }

    private <T> T invoke(AiOperation operation, String model, String promptVersion, Supplier<T> invocation) {
        long startedAt = System.nanoTime();
        try {
            T result = invocation.get();
            log.info("KI-Aufruf provider=openai model={} promptVersion={} schemaVersion={} type={} durationMs={} result=success",
                    model, promptVersion, schemaVersion(operation), operation, elapsedMillis(startedAt));
            return result;
        } catch (AiTechnicalException exception) {
            log.warn("KI-Aufruf provider=openai model={} promptVersion={} schemaVersion={} type={} durationMs={} errorCode={}",
                    model, promptVersion, schemaVersion(operation), operation, elapsedMillis(startedAt),
                    exception.getErrorCode());
            throw exception;
        }
    }

    private String schemaVersion(AiOperation operation) {
        return operation == AiOperation.PRE_CHECK ? AiSchemaVersions.PRE_CHECK : AiSchemaVersions.GENERATING_PLAN;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private GeneratedPlanResponse map(OpenAiGenerationOutput output) {
        return new GeneratedPlanResponse(mapList(output.phases(), this::map));
    }

    private GeneratedPhase map(OpenAiGenerationOutput.Phase phase) {
        return new GeneratedPhase(
                nullable(phase.tempId()), phase.title(), nullable(phase.description()),
                nullable(phase.startDate()), nullable(phase.endDate()), phase.order(),
                mapList(phase.tasks(), this::map), mapList(phase.milestones(), this::map));
    }

    private GeneratedTask map(OpenAiGenerationOutput.Task task) {
        return new GeneratedTask(
                task.tempId(), task.title(), nullable(task.description()),
                nullable(task.estimatedHours()), nullable(task.startDate()),
                nullable(task.dueDate()), nullable(task.criticalAssumption()),
                task.origin(), task.order(), task.prerequisiteTaskTempIds(), mapPriority(task.priority()));
    }

    private GeneratedMilestone map(OpenAiGenerationOutput.Milestone milestone) {
        return new GeneratedMilestone(
                nullable(milestone.tempId()), milestone.title(), nullable(milestone.date()), milestone.order());
    }

    private <T> T nullable(Optional<T> value) {
        return value == null ? null : value.orElse(null);
    }

    private <T> T requireOutput(T output) {
        if (output == null) throw new AiOutputValidationException("OpenAI lieferte einen leeren Ausgabewert.");
        return output;
    }

    private <T, R> List<R> mapList(List<T> values, java.util.function.Function<T, R> mapper) {
        return values == null ? null : values.stream().map(this::requireOutput).map(mapper).toList();
    }

    private TaskPriority mapPriority(Optional<String> priority) {
        String value = nullable(priority);
        if (value == null) {
            return null;
        }
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new AiOutputValidationException(
                    "OpenAI lieferte eine unbekannte Aufgabenprioritaet: " + value);
        }
    }
}

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
        OpenAiPreCheckOutput output = invoke(
                AiOperation.PRE_CHECK, properties.getPreCheckModel(), prompt.version(),
                () -> gateway.execute(properties.getPreCheckModel(), prompt, OpenAiPreCheckOutput.class));
        return output == null ? null : new AiPreCheckResult(output.problems());
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        var prompt = generationPromptBuilder.build(request);
        OpenAiGenerationOutput output = invoke(
                AiOperation.PLAN_GENERATION, properties.getGenerationModel(), prompt.version(),
                () -> gateway.execute(properties.getGenerationModel(), prompt, OpenAiGenerationOutput.class));
        return map(output);
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
        return operation == AiOperation.PRE_CHECK ? AiSchemaVersions.PRE_CHECK : AiSchemaVersions.GENERATION;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private GeneratedPlanResponse map(OpenAiGenerationOutput output) {
        if (output == null) {
            return null;
        }
        if (output.metadata() == null || output.phases() == null) {
            return new GeneratedPlanResponse(null, null);
        }
        var metadata = new GeneratedPlanMetadata(output.metadata().summary(), output.metadata().assumptions());
        List<GeneratedPhase> phases = output.phases().stream()
                .map(phase -> phase == null ? null : map(phase)).toList();
        return new GeneratedPlanResponse(metadata, phases);
    }

    private GeneratedPhase map(OpenAiGenerationOutput.Phase phase) {
        return new GeneratedPhase(
                nullable(phase.tempId()), phase.title(), nullable(phase.description()),
                nullable(phase.startDate()), nullable(phase.endDate()), phase.order(),
                phase.tasks() == null ? null : phase.tasks().stream()
                        .map(task -> task == null ? null : map(task)).toList(),
                phase.milestones() == null ? null : phase.milestones().stream()
                        .map(milestone -> milestone == null ? null : map(milestone)).toList());
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

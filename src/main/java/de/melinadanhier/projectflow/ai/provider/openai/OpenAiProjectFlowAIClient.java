package de.melinadanhier.projectflow.ai.provider.openai;

import java.util.List;
import java.util.function.Supplier;

import de.melinadanhier.projectflow.ai.AiClient;
import de.melinadanhier.projectflow.ai.exception.AiClientTechnicalException;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import lombok.extern.slf4j.Slf4j;

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
        var prompt = preCheckPromptBuilder.build(request.confirmedWizardData());
        OpenAiPreCheckOutput output = invoke(
                "PRE_CHECK", properties.getPreCheckModel(), prompt.version(),
                () -> gateway.execute(properties.getPreCheckModel(), prompt, OpenAiPreCheckOutput.class));
        return new AiPreCheckResult(output.problems());
    }

    @Override
    public GeneratedPlanResponse generatePlan(AiGenerationRequest request) {
        var prompt = generationPromptBuilder.build(request);
        OpenAiGenerationOutput output = invoke(
                "GENERATION", properties.getGenerationModel(), prompt.version(),
                () -> gateway.execute(properties.getGenerationModel(), prompt, OpenAiGenerationOutput.class));
        return map(output);
    }

    private <T> T invoke(String callType, String model, String promptVersion, Supplier<T> invocation) {
        long startedAt = System.nanoTime();
        try {
            T result = invocation.get();
            log.info("KI-Aufruf provider=openai model={} promptVersion={} type={} durationMs={} result=success",
                    model, promptVersion, callType, elapsedMillis(startedAt));
            return result;
        } catch (AiClientTechnicalException exception) {
            log.warn("KI-Aufruf provider=openai model={} promptVersion={} type={} durationMs={} result={}",
                    model, promptVersion, callType, elapsedMillis(startedAt),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private GeneratedPlanResponse map(OpenAiGenerationOutput output) {
        var metadata = new GeneratedPlanMetadata(output.metadata().summary(), output.metadata().assumptions());
        List<GeneratedPhase> phases = output.phases().stream().map(this::map).toList();
        return new GeneratedPlanResponse(metadata, phases);
    }

    private GeneratedPhase map(OpenAiGenerationOutput.Phase phase) {
        return new GeneratedPhase(
                phase.tempId(), phase.title(), phase.description().orElse(null),
                phase.startDate().orElse(null), phase.endDate().orElse(null), phase.order(),
                phase.tasks().stream().map(this::map).toList(),
                phase.milestones().stream().map(this::map).toList());
    }

    private GeneratedTask map(OpenAiGenerationOutput.Task task) {
        return new GeneratedTask(
                task.tempId(), task.title(), task.description().orElse(null),
                task.estimatedHours().orElse(null), task.startDate().orElse(null),
                task.dueDate().orElse(null), task.criticalAssumption().orElse(null),
                task.origin(), task.order());
    }

    private GeneratedMilestone map(OpenAiGenerationOutput.Milestone milestone) {
        return new GeneratedMilestone(
                milestone.tempId(), milestone.title(), milestone.date().orElse(null), milestone.order());
    }
}

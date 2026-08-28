package de.melinadanhier.projectflow.ai.provider.openai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedMilestone;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPhase;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedTask;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.AbstractProviderAiClient;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;

import java.util.List;
import java.util.Optional;

public class OpenAiProjectFlowAIClient extends AbstractProviderAiClient<OpenAiGenerationOutput> {

    public OpenAiProjectFlowAIClient(
            AiResponsesGateway gateway,
            OpenAiProperties properties,
            PreCheckPromptBuilder preCheckPromptBuilder,
            GenerationPromptBuilder generationPromptBuilder
    ) {
        super(
                "openai",
                gateway,
                properties::getPreCheckModel,
                properties::getGenerationModel,
                OpenAiGenerationOutput.class,
                preCheckPromptBuilder,
                generationPromptBuilder
        );
    }

    @Override
    protected GeneratedPlanResponse mapPlan(OpenAiGenerationOutput output) {
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

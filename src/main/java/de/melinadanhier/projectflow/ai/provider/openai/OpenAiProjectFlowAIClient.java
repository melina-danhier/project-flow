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
import java.util.function.Function;

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
                phase.tempId().orElse(null),
                phase.title(),
                phase.description().orElse(null),
                phase.startDate().orElse(null),
                phase.endDate().orElse(null),
                phase.order(),
                mapList(phase.tasks(), this::map),
                mapList(phase.milestones(), this::map)
        );
    }

    private GeneratedTask map(OpenAiGenerationOutput.Task task) {
        return new GeneratedTask(
                task.tempId(),
                task.title(),
                task.description().orElse(null),
                task.estimatedHours().orElse(null),
                task.startDate().orElse(null),
                task.dueDate().orElse(null),
                task.criticalAssumption().orElse(null),
                task.origin(),
                task.order(),
                task.prerequisiteTaskTempIds(),
                mapPriority(task.priority().orElse(null))
        );
    }

    private GeneratedMilestone map(OpenAiGenerationOutput.Milestone milestone) {
        return new GeneratedMilestone(
                milestone.tempId().orElse(null),
                milestone.title(),
                milestone.date().orElse(null),
                milestone.order()
        );
    }

    private <T, R> List<R> mapList(List<T> values, Function<T, R> mapper) {
        return values == null ? null : values.stream().map(this::requireOutput).map(mapper).toList();
    }

    private TaskPriority mapPriority(String value) {
        if (value == null) return null;
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new AiOutputValidationException(
                    "OpenAI lieferte eine unbekannte Aufgabenpriorität: " + value
            );
        }
    }
}

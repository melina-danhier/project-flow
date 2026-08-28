package de.melinadanhier.projectflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedElementOrigin;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.openai.*;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import com.openai.models.responses.StructuredResponseCreateParams;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiProjectFlowAIClientTest {

    @Test
    void normalizesNullOptionalsInCanonicalAndConvenienceConstructors() {
        var phase = new OpenAiGenerationOutput.Phase(
                (Optional<String>) null, "Phase", null, null, null, 1, List.of(), List.of());
        var task = new OpenAiGenerationOutput.Task(
                "task-1", "Aufgabe", null, null, null, null, null,
                GeneratedElementOrigin.AI_INFERRED, 1, List.of(), null);
        var milestone = new OpenAiGenerationOutput.Milestone((Optional<String>) null, "Meilenstein", null, 1);

        assertThat(Arrays.asList(
                phase.tempId(), phase.description(), phase.startDate(), phase.endDate(),
                task.description(), task.estimatedHours(), task.startDate(), task.dueDate(),
                task.criticalAssumption(), task.priority(), milestone.tempId(), milestone.date()))
                .allSatisfy(value -> assertThat(value).isEmpty());

        assertThat(new OpenAiGenerationOutput.Phase(
                (String) null, "Phase", null, null, null, 1, List.of(), List.of())).isEqualTo(phase);
        assertThat(new OpenAiGenerationOutput.Task(
                "task-1", "Aufgabe", null, null, null, null, null,
                GeneratedElementOrigin.AI_INFERRED, 1)).isEqualTo(task);
        assertThat(new OpenAiGenerationOutput.Milestone((String) null, "Meilenstein", null, 1))
                .isEqualTo(milestone);
    }

    @Test
    void preservesPresentOptionalValuesInAllOutputRecords() {
        var text = Optional.of("value");
        var date = Optional.of(LocalDate.of(2026, 9, 1));
        var hours = Optional.of(3);
        var phase = new OpenAiGenerationOutput.Phase(text, "Phase", text, date, date, 1, List.of(), List.of());
        var task = new OpenAiGenerationOutput.Task(
                "task-1", "Aufgabe", text, hours, date, date, text,
                GeneratedElementOrigin.AI_INFERRED, 1, List.of(), text);
        var milestone = new OpenAiGenerationOutput.Milestone(text, "Meilenstein", date, 1);

        assertThat(List.of(phase.tempId(), phase.description(), task.description(),
                task.criticalAssumption(), task.priority(), milestone.tempId())).containsOnly(text);
        assertThat(List.of(phase.startDate(), phase.endDate(), task.startDate(), task.dueDate(), milestone.date()))
                .containsOnly(date);
        assertThat(task.estimatedHours()).isEqualTo(hours);
    }

    @Test
    void sdkUsesOptionalToMakeJsonFieldsNullable() {
        var optionalSchema = schemaFor(OpenAiGenerationOutput.Phase.class);
        assertThat(optionalSchema.path("properties").path("description").path("type"))
                .containsExactlyInAnyOrder(
                        TextNode.valueOf("string"), TextNode.valueOf("null"));

    }

    private <T> JsonNode schemaFor(Class<T> type) {
        var format = StructuredResponseCreateParams.<T>builder()
                .model("test-model").input("test").text(type).build()
                .rawParams().text().orElseThrow().format().orElseThrow().asJsonSchema();
        return com.openai.core.ObjectMappers.jsonMapper().valueToTree(format).path("schema");
    }

    @Test
    void mapsBothStructuredResponsesAndUsesDedicatedModels() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setPreCheckModel("precheck-model");
        properties.setGenerationModel("generation-model");
        PreCheckPromptBuilder preCheckPrompts = mock(PreCheckPromptBuilder.class);
        GenerationPromptBuilder generationPrompts = mock(GenerationPromptBuilder.class);
        AiPrompt preCheckPrompt = new AiPrompt("precheck-v1", "pre", "data");
        AiPrompt generationPrompt = new AiPrompt("generation-v1", "generation", "data");
        AiWizardSnapshot snapshot = snapshot();
        AiGenerationRequest generationRequest = new AiGenerationRequest(snapshot, List.of());
        when(preCheckPrompts.build(new AiPreCheckRequest(snapshot))).thenReturn(preCheckPrompt);
        when(generationPrompts.build(generationRequest)).thenReturn(generationPrompt);
        RecordingGateway gateway = new RecordingGateway();
        OpenAiProjectFlowAIClient client = new OpenAiProjectFlowAIClient(gateway, properties, preCheckPrompts, generationPrompts);

        var preCheck = client.preCheck(new AiPreCheckRequest(snapshot));
        var plan = client.generatePlan(generationRequest);

        assertThat(preCheck.problems()).singleElement()
                .extracting("severity").isEqualTo(AiPreCheckSeverity.WARNING);
        assertThat(plan.phases()).singleElement().satisfies(phase -> {
            assertThat(phase.title()).isEqualTo("Phase");
            assertThat(phase.description()).isNull();
            assertThat(phase.tasks()).singleElement()
                    .extracting("title").isEqualTo("Aufgabe");
        });
        assertThat(gateway.calls).containsExactly(
                "precheck-model:precheck-v1:AiPreCheckResult",
                "generation-model:generation-v1:OpenAiGenerationOutput");
    }

    @Test
    void rejectsUnknownPriorityFromStructuredProviderOutput() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setGenerationModel("generation-model");
        GenerationPromptBuilder generationPrompts = mock(GenerationPromptBuilder.class);
        AiGenerationRequest request = new AiGenerationRequest(snapshot(), List.of());
        when(generationPrompts.build(request))
                .thenReturn(new AiPrompt("generation-v1", "generation", "data"));
        OpenAiProjectFlowAIClient client = new OpenAiProjectFlowAIClient(
                new RecordingGateway(Optional.of("URGENT")), properties,
                mock(PreCheckPromptBuilder.class), generationPrompts);

        assertThatThrownBy(() -> client.generatePlan(request))
                .isInstanceOf(AiOutputValidationException.class);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }

    @Test
    void rejectsMissingOutputAndNullListElementsAsValidationFailure() {
        var gateway = mock(AiResponsesGateway.class);
        var prompts = mock(GenerationPromptBuilder.class);
        var request = new AiGenerationRequest(snapshot(), List.of());
        var properties = new OpenAiProperties();
        var prompt = new AiPrompt("v1", "instructions", "data");
        when(prompts.build(request)).thenReturn(prompt);
        var client = new OpenAiProjectFlowAIClient(gateway, properties, mock(PreCheckPromptBuilder.class), prompts);
        var nullTasks = new OpenAiGenerationOutput.Phase("phase", "Phase", Optional.empty(), Optional.empty(),
                Optional.empty(), 1, java.util.Arrays.asList((OpenAiGenerationOutput.Task) null), List.of());
        var nullMilestones = new OpenAiGenerationOutput.Phase("phase", "Phase", Optional.empty(), Optional.empty(),
                Optional.empty(), 1, List.of(), java.util.Arrays.asList((OpenAiGenerationOutput.Milestone) null));
        for (var output : java.util.Arrays.asList(null,
                new OpenAiGenerationOutput(java.util.Arrays.asList((OpenAiGenerationOutput.Phase) null)),
                new OpenAiGenerationOutput(List.of(nullTasks)), new OpenAiGenerationOutput(List.of(nullMilestones)))) {
            when(gateway.execute(properties.getGenerationModel(), prompt, OpenAiGenerationOutput.class)).thenReturn(output);
            assertThatThrownBy(() -> client.generatePlan(request)).isInstanceOf(AiOutputValidationException.class);
        }
    }

    private static class RecordingGateway implements AiResponsesGateway {
        private final java.util.ArrayList<String> calls = new java.util.ArrayList<>();
        private final Optional<String> priority;

        private RecordingGateway() {
            this(Optional.empty());
        }

        private RecordingGateway(Optional<String> priority) {
            this.priority = priority;
        }

        @Override
        public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
            calls.add(model + ":" + prompt.version() + ":" + responseType.getSimpleName());
            Object output;
            if (responseType == AiPreCheckResult.class) {
                output = new AiPreCheckResult(List.of(new AiPreCheckProblem(
                        AiPreCheckSeverity.WARNING, "Knapp", "Mehr Zeit einplanen")));
            } else {
                output = new OpenAiGenerationOutput(
                        List.of(new OpenAiGenerationOutput.Phase(
                                "phase-1", "Phase", null, null, null, 1,
                                List.of(new OpenAiGenerationOutput.Task(
                                        "task-1", "Aufgabe", null, Optional.of(1),
                                        null, null, null,
                                        GeneratedElementOrigin.AI_INFERRED, 1, List.of(), priority)),
                                List.of())));
            }
            return responseType.cast(output);
        }
    }
}

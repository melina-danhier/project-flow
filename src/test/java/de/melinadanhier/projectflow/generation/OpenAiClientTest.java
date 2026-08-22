package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.generation.client.OpenAiClient;
import de.melinadanhier.projectflow.generation.client.OpenAiProperties;
import de.melinadanhier.projectflow.generation.client.OpenAiGenerationOutput;
import de.melinadanhier.projectflow.generation.client.OpenAiPreCheckOutput;
import de.melinadanhier.projectflow.generation.client.OpenAiResponsesGateway;
import de.melinadanhier.projectflow.generation.dto.request.AiGenerationRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedElementOrigin;
import de.melinadanhier.projectflow.generation.prompt.AiPrompt;
import de.melinadanhier.projectflow.generation.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.generation.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import com.openai.models.responses.StructuredResponseCreateParams;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiClientTest {

    @Test
    void sdkCanDeriveStrictSchemasForBothOutputTypes() {
        assertThat(StructuredResponseCreateParams.<OpenAiPreCheckOutput>builder()
                .model("test-model")
                .input("test")
                .text(OpenAiPreCheckOutput.class)
                .build()).isNotNull();
        assertThat(StructuredResponseCreateParams.<OpenAiGenerationOutput>builder()
                .model("test-model")
                .input("test")
                .text(OpenAiGenerationOutput.class)
                .build()).isNotNull();
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
        when(preCheckPrompts.build(snapshot)).thenReturn(preCheckPrompt);
        when(generationPrompts.build(generationRequest)).thenReturn(generationPrompt);
        RecordingGateway gateway = new RecordingGateway();
        OpenAiClient client = new OpenAiClient(gateway, properties, preCheckPrompts, generationPrompts);

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
                "precheck-model:precheck-v1:OpenAiPreCheckOutput",
                "generation-model:generation-v1:OpenAiGenerationOutput");
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }

    private static class RecordingGateway implements OpenAiResponsesGateway {
        private final java.util.ArrayList<String> calls = new java.util.ArrayList<>();

        @Override
        public <T> T execute(String model, AiPrompt prompt, Class<T> responseType) {
            calls.add(model + ":" + prompt.version() + ":" + responseType.getSimpleName());
            Object output;
            if (responseType == OpenAiPreCheckOutput.class) {
                output = new OpenAiPreCheckOutput(List.of(new AiPreCheckProblem(
                        AiPreCheckSeverity.WARNING, "Knapp", "Mehr Zeit einplanen")));
            } else {
                output = new OpenAiGenerationOutput(
                        new OpenAiGenerationOutput.Metadata("Plan", List.of()),
                        List.of(new OpenAiGenerationOutput.Phase(
                                "phase-1", "Phase", Optional.empty(), Optional.empty(), Optional.empty(), 1,
                                List.of(new OpenAiGenerationOutput.Task(
                                        "task-1", "Aufgabe", Optional.empty(), Optional.of(1),
                                        Optional.empty(), Optional.empty(), Optional.empty(),
                                        GeneratedElementOrigin.AI_INFERRED, 1)),
                                List.of())));
            }
            return responseType.cast(output);
        }
    }
}

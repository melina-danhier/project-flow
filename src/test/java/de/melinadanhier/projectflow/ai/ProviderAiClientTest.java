package de.melinadanhier.projectflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.improvement.AiFeedbackType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementContent;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementElementType;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementProjectContext;
import de.melinadanhier.projectflow.ai.model.improvement.AiImprovementRequest;
import de.melinadanhier.projectflow.ai.model.improvement.AiReplanPlacementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTextImprovementResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskReplanResponse;
import de.melinadanhier.projectflow.ai.model.improvement.AiTaskEffortResponse;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.ImprovementPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiAiClient;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiProperties;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiGenerationOutput;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProperties;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiReplanOutput;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import de.melinadanhier.projectflow.planelement.model.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderAiClientTest {

    private final AiResponsesGateway gateway = mock(AiResponsesGateway.class);
    private final PreCheckPromptBuilder preCheckPrompts = mock(PreCheckPromptBuilder.class);
    private final GenerationPromptBuilder generationPrompts = mock(GenerationPromptBuilder.class);
    private final ImprovementPromptBuilder improvementPrompts = mock(ImprovementPromptBuilder.class);
    private final AiPrompt prompt = new AiPrompt("v1", "instructions", "confirmed data");
    private final AiWizardSnapshot snapshot = new AiWizardSnapshot(
            "Projekt", null, null, null, CollaborationMode.INDIVIDUAL, ProjectCategory.OTHER,
            null, "Test", null, null, null);

    @Test
    void openAiReplanSchemaAllowsNullForEveryOptionalField() throws Exception {
        Class<?> structuredOutputs = Class.forName("com.openai.core.StructuredOutputsKt");
        Method extractSchema = structuredOutputs.getDeclaredMethod("extractSchema", Class.class);
        JsonNode taskSchema = (JsonNode) extractSchema.invoke(null, OpenAiReplanOutput.Task.class);

        assertNullable(taskSchema, "startDate");
        assertNullable(taskSchema, "dueDate");
        assertNullable(taskSchema, "targetSectionId");
        assertNullable(taskSchema, "beforeElementId");
        assertNullable(taskSchema, "afterElementId");
    }

    @ParameterizedTest
    @MethodSource("providerOperations")
    void rejectsMissingGatewayOutput(String provider, AiOperation operation) {
        AiClient client = client(provider);

        assertThatThrownBy(() -> invoke(client, operation)).isInstanceOf(AiOutputValidationException.class);

        verify(gateway).execute(anyString(), eq(prompt), eq(responseType(provider, operation)));
        verifyNoMoreInteractions(gateway);
    }

    @ParameterizedTest
    @MethodSource("providerOperations")
    void propagatesTechnicalAndProgrammingFailuresWithoutWrappingOrRetrying(String provider, AiOperation operation) {
        AiClient client = client(provider);
        List<RuntimeException> failures = List.of(
                new AiTechnicalException(AiTechnicalErrorCode.PROVIDER_TIMEOUT, "Timeout"),
                new IllegalStateException("Programming error"));
        for (RuntimeException failure : failures) {
            doThrow(failure).when(gateway).execute(anyString(), eq(prompt), any());

            assertThatThrownBy(() -> invoke(client, operation)).isSameAs(failure);
        }

        verify(gateway, times(failures.size())).execute(anyString(), eq(prompt), eq(responseType(provider, operation)));
        verifyNoMoreInteractions(gateway);
    }

    @ParameterizedTest
    @MethodSource("providerOperations")
    void promptFailureDoesNotCallGateway(String provider, AiOperation operation) {
        AiClient client = client(provider);
        var failure = new AiTechnicalException(AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, "Invalid prompt version");
        if (operation == AiOperation.PRE_CHECK) {
            when(preCheckPrompts.build(any(AiPreCheckRequest.class))).thenThrow(failure);
        } else {
            when(generationPrompts.build(any(AiGenerationRequest.class))).thenThrow(failure);
        }

        assertThatThrownBy(() -> invoke(client, operation)).isSameAs(failure);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @MethodSource("providers")
    void sectionImprovementUsesSectionOnlyResponseContract(String provider) {
        AiClient client = client(provider);
        var request = new AiImprovementRequest(AiFeedbackType.IMPROVE, null,
                new AiImprovementProjectContext("Projekt", null, null, null),
                new AiImprovementContent(AiImprovementElementType.SECTION, "Phase", "Beschreibung",
                        null, null, null, null));
        var output = new AiTextImprovementResponse("Präzise Phase", "Präzise Beschreibung");
        when(improvementPrompts.build(request)).thenReturn(prompt);
        doReturn(output).when(gateway)
                .execute(anyString(), eq(prompt), eq(AiTextImprovementResponse.class));

        var response = client.improveElement(request);

        assertThat(response.elementType()).isEqualTo(AiImprovementElementType.SECTION);
        assertThat(response.title()).isEqualTo("Präzise Phase");
        assertThat(response.description()).isEqualTo("Präzise Beschreibung");
        assertThat(response.startDate()).isNull();
        assertThat(response.dueDate()).isNull();
        verify(gateway).execute(anyString(), eq(prompt), eq(AiTextImprovementResponse.class));
    }

    @ParameterizedTest
    @MethodSource("taskTextImprovementCases")
    void taskImprovementWithoutExplicitPlanningRequestUsesTextOnlyContract(
            String provider, AiFeedbackType feedbackType) {
        AiClient client = client(provider);
        var request = new AiImprovementRequest(feedbackType, null,
                new AiImprovementProjectContext("Projekt", null, null, null),
                new AiImprovementContent(AiImprovementElementType.TASK, "Packen", null,
                        TaskPriority.MEDIUM, null, null, null));
        var output = new AiTextImprovementResponse("Kartons strukturiert packen", "Nach Räumen sortieren");
        when(improvementPrompts.build(request)).thenReturn(prompt);
        doReturn(output).when(gateway)
                .execute(anyString(), eq(prompt), eq(AiTextImprovementResponse.class));

        var response = client.improveElement(request);

        assertThat(response.title()).isEqualTo("Kartons strukturiert packen");
        assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(response.estimatedHours()).isNull();
        assertThat(response.startDate()).isNull();
        assertThat(response.dueDate()).isNull();
        verify(gateway).execute(anyString(), eq(prompt), eq(AiTextImprovementResponse.class));
    }

    @ParameterizedTest
    @MethodSource("providers")
    void taskReplanUsesProviderSpecificContract(String provider) {
        AiClient client = client(provider);
        var original = new AiImprovementContent(AiImprovementElementType.TASK, "Packen", "Kartons packen",
                TaskPriority.MEDIUM, 2, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN, null, null, null, null, original);
        LocalDate expectedStart = LocalDate.of(2026, 9, 11);
        LocalDate expectedDue = LocalDate.of(2026, 9, 14);
        String explanation = "Die Aufgabe liegt vor dem nächsten abhängigen Schritt.";
        when(improvementPrompts.build(request)).thenReturn(prompt);
        if (provider.equals("openai")) {
            var output = new OpenAiReplanOutput.Task(
                    Optional.of(expectedStart), Optional.of(expectedDue), unchangedOpenAiPlacement(), explanation);
            doReturn(output).when(gateway).execute(anyString(), eq(prompt), eq(OpenAiReplanOutput.Task.class));
        } else {
            var output = new AiTaskReplanResponse(
                    expectedStart, expectedDue, AiReplanPlacementResponse.unchanged(), explanation);
            doReturn(output).when(gateway).execute(anyString(), eq(prompt), eq(AiTaskReplanResponse.class));
        }

        var response = client.improveElement(request);

        assertThat(response.title()).isEqualTo(original.title());
        assertThat(response.estimatedHours()).isEqualTo(original.estimatedHours());
        assertThat(response.startDate()).isEqualTo(expectedStart);
        assertThat(response.dueDate()).isEqualTo(expectedDue);
        assertThat(response.placement()).isEqualTo(AiReplanPlacementResponse.unchanged());
        assertThat(response.explanation()).isEqualTo(explanation);
    }

    @ParameterizedTest
    @MethodSource("providers")
    void unchangedReplanPlacementDiscardsRedundantIdsFromProvider(String provider) {
        AiClient client = client(provider);
        var original = new AiImprovementContent(AiImprovementElementType.TASK, "Packen", null,
                TaskPriority.MEDIUM, 2, null, null);
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN, null, null, null, null, original);
        String redundantId = java.util.UUID.randomUUID().toString();
        when(improvementPrompts.build(request)).thenReturn(prompt);
        if (provider.equals("openai")) {
            var output = new OpenAiReplanOutput.Task(
                    Optional.empty(), Optional.empty(),
                    new OpenAiReplanOutput.Placement(false, Optional.of(redundantId),
                            Optional.empty(), Optional.empty()),
                    "Die bestehende Planung bleibt sinnvoll.");
            doReturn(output).when(gateway).execute(anyString(), eq(prompt), eq(OpenAiReplanOutput.Task.class));
        } else {
            var output = new AiTaskReplanResponse(null, null,
                    new AiReplanPlacementResponse(false, redundantId, null, null),
                    "Die bestehende Planung bleibt sinnvoll.");
            doReturn(output).when(gateway).execute(anyString(), eq(prompt), eq(AiTaskReplanResponse.class));
        }

        var response = client.improveElement(request);

        assertThat(response.placement()).isEqualTo(AiReplanPlacementResponse.unchanged());
    }

    @ParameterizedTest
    @MethodSource("openAiReplanCases")
    void openAiReplanMapsNullableSchemaFields(
            AiImprovementElementType elementType, Object providerOutput, Class<?> responseType) {
        AiClient client = client("openai");
        var original = new AiImprovementContent(elementType, "Element", null,
                elementType == AiImprovementElementType.TASK ? TaskPriority.MEDIUM : null,
                null, null, null);
        var request = new AiImprovementRequest(AiFeedbackType.REPLAN, null, null, null, null, original);
        when(improvementPrompts.build(request)).thenReturn(prompt);
        doReturn(providerOutput).when(gateway).execute(anyString(), eq(prompt), eq(responseType));

        var response = client.improveElement(request);

        assertThat(response.startDate()).isNull();
        assertThat(response.dueDate()).isNull();
        assertThat(response.placement()).isEqualTo(AiReplanPlacementResponse.unchanged());
        verify(gateway).execute(anyString(), eq(prompt), eq(responseType));
    }

    @ParameterizedTest
    @MethodSource("providers")
    void effortEstimationUsesEffortOnlyContract(String provider) {
        AiClient client = client(provider);
        var original = new AiImprovementContent(AiImprovementElementType.TASK, "Packen", null,
                TaskPriority.MEDIUM, 2, null, null);
        var request = new AiImprovementRequest(AiFeedbackType.ESTIMATE_EFFORT, null, null, null, null, original);
        var output = new AiTaskEffortResponse(5, "Der Umfang entspricht etwa fünf Arbeitsstunden.");
        when(improvementPrompts.build(request)).thenReturn(prompt);
        doReturn(output).when(gateway).execute(anyString(), eq(prompt), eq(AiTaskEffortResponse.class));

        var response = client.improveElement(request);

        assertThat(response.estimatedHours()).isEqualTo(5);
        assertThat(response.title()).isEqualTo(original.title());
        assertThat(response.startDate()).isNull();
        assertThat(response.dueDate()).isNull();
        assertThat(response.explanation()).isEqualTo(output.explanation());
    }

    private AiClient client(String provider) {
        when(preCheckPrompts.build(any(AiPreCheckRequest.class))).thenReturn(prompt);
        when(generationPrompts.build(any(AiGenerationRequest.class))).thenReturn(prompt);
        return switch (provider) {
            case "openai" -> new OpenAiProjectFlowAIClient(
                    gateway, new OpenAiProperties(), preCheckPrompts, generationPrompts, improvementPrompts);
            case "gemini" -> new GeminiAiClient(
                    gateway, new GeminiProperties(), preCheckPrompts, generationPrompts, improvementPrompts);
            default -> throw new IllegalArgumentException(provider);
        };
    }

    private void invoke(AiClient client, AiOperation operation) {
        switch (operation) {
            case PRE_CHECK -> client.preCheck(new AiPreCheckRequest(snapshot));
            case PLAN_GENERATION -> client.generatePlan(new AiGenerationRequest(snapshot, List.of()));
        }
    }

    private Class<?> responseType(String provider, AiOperation operation) {
        if (operation == AiOperation.PRE_CHECK) {
            return AiPreCheckResult.class;
        }
        return provider.equals("openai") ? OpenAiGenerationOutput.class : GeneratedPlanResponse.class;
    }

    private static Stream<Arguments> providerOperations() {
        return providers()
                .flatMap(provider -> Stream.of(AiOperation.values()).map(operation -> Arguments.of(provider, operation)));
    }

    private static Stream<String> providers() {
        return Stream.of("openai", "gemini");
    }

    private static Stream<Arguments> taskTextImprovementCases() {
        return providers().flatMap(provider -> Stream.of(
                        AiFeedbackType.IMPROVE, AiFeedbackType.EXPAND, AiFeedbackType.SIMPLIFY)
                .map(feedbackType -> Arguments.of(provider, feedbackType)));
    }

    private static Stream<Arguments> openAiReplanCases() {
        String explanation = "Die bisherige Planung bleibt sinnvoll.";
        return Stream.of(
                Arguments.of(AiImprovementElementType.TASK,
                        new OpenAiReplanOutput.Task(
                                Optional.empty(), Optional.empty(), unchangedOpenAiPlacement(), explanation),
                        OpenAiReplanOutput.Task.class),
                Arguments.of(AiImprovementElementType.MILESTONE,
                        new OpenAiReplanOutput.Milestone(
                                Optional.empty(), unchangedOpenAiPlacement(), explanation),
                        OpenAiReplanOutput.Milestone.class));
    }

    private static OpenAiReplanOutput.Placement unchangedOpenAiPlacement() {
        return new OpenAiReplanOutput.Placement(
                false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private void assertNullable(JsonNode schema, String propertyName) {
        JsonNode property = findProperty(schema, propertyName);
        assertThat(property)
                .as("Schema-Eigenschaft %s", propertyName)
                .isNotNull();
        boolean nullableThroughAnyOf = property.path("anyOf").findValuesAsText("type").contains("null");
        boolean nullableThroughTypeArray = false;
        if (property.path("type").isArray()) {
            for (JsonNode type : property.path("type")) {
                nullableThroughTypeArray |= type.asText().equals("null");
            }
        }
        assertThat(nullableThroughAnyOf || nullableThroughTypeArray)
                .as("Nullable Schema-Eigenschaft %s: %s", propertyName, property)
                .isTrue();
    }

    private JsonNode findProperty(JsonNode node, String propertyName) {
        if (node == null) return null;
        JsonNode properties = node.get("properties");
        if (properties != null && properties.has(propertyName)) {
            return properties.get(propertyName);
        }
        for (JsonNode child : node) {
            JsonNode match = findProperty(child, propertyName);
            if (match != null) return match;
        }
        return null;
    }
}

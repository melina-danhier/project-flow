package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.AiGenerationRequest;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.provider.AiClient;
import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiAiClient;
import de.melinadanhier.projectflow.ai.provider.gemini.GeminiProperties;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiGenerationOutput;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProjectFlowAIClient;
import de.melinadanhier.projectflow.ai.provider.openai.OpenAiProperties;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderAiClientTest {

    private final AiResponsesGateway gateway = mock(AiResponsesGateway.class);
    private final PreCheckPromptBuilder preCheckPrompts = mock(PreCheckPromptBuilder.class);
    private final GenerationPromptBuilder generationPrompts = mock(GenerationPromptBuilder.class);
    private final AiPrompt prompt = new AiPrompt("v1", "instructions", "confirmed data");
    private final AiWizardSnapshot snapshot = new AiWizardSnapshot(
            "Projekt", null, null, null, CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER,
            "Test", null, null, null);

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

    private AiClient client(String provider) {
        when(preCheckPrompts.build(any(AiPreCheckRequest.class))).thenReturn(prompt);
        when(generationPrompts.build(any(AiGenerationRequest.class))).thenReturn(prompt);
        return switch (provider) {
            case "openai" -> new OpenAiProjectFlowAIClient(
                    gateway, new OpenAiProperties(), preCheckPrompts, generationPrompts);
            case "gemini" -> new GeminiAiClient(gateway, new GeminiProperties(), preCheckPrompts, generationPrompts);
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
        return Stream.of("openai", "gemini")
                .flatMap(provider -> Stream.of(AiOperation.values()).map(operation -> Arguments.of(provider, operation)));
    }
}

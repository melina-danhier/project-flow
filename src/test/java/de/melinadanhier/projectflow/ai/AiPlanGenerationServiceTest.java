package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.provider.AiResponsesGateway;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.prompt.AiPromptVersions;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import de.melinadanhier.projectflow.ai.provider.openai.*;
import de.melinadanhier.projectflow.ai.provider.gemini.*;
import de.melinadanhier.projectflow.ai.prompt.GenerationPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.PreCheckPromptBuilder;
import de.melinadanhier.projectflow.ai.prompt.AiPrompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiPlanGenerationServiceTest {

    @Test
    void rejectsInvalidAcknowledgedWarningsBeforeProviderCallOrAttemptRecording() {
        var client = mock(AiClient.class);
        var backoff = mock(AiRetryBackoff.class);
        var beforeProviderCall = mock(Runnable.class);
        var error = new AiPreCheckProblem(AiPreCheckSeverity.ERROR, "Unmöglich", "Ziel ändern");

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of(error),
                0, AiPromptVersions.GENERATION_PROMPT, beforeProviderCall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acknowledgedWarnings");
        verifyNoInteractions(client, backoff, beforeProviderCall);
    }

    @Test
    void everyRealAdapterStillPassesThroughCommonDomainValidation() {
        var generationPrompts = mock(GenerationPromptBuilder.class);
        var prePrompts = mock(PreCheckPromptBuilder.class);
        var prompt = new AiPrompt("v1", "instructions", "data");
        when(generationPrompts.build(any(AiGenerationRequest.class))).thenReturn(prompt);
        var openAiGateway = mock(AiResponsesGateway.class);
        when(openAiGateway.execute(anyString(), any(), eq(OpenAiGenerationOutput.class)))
                .thenReturn(new OpenAiGenerationOutput(List.of()));
        var geminiGateway = mock(AiResponsesGateway.class);
        when(geminiGateway.execute(anyString(), any(), eq(GeneratedPlanResponse.class))).thenReturn(emptyPlan());
        for (AiClient client : List.of(
                new OpenAiProjectFlowAIClient(openAiGateway, new OpenAiProperties(), prePrompts, generationPrompts),
                new GeminiAiClient(geminiGateway, new GeminiProperties(), prePrompts, generationPrompts))) {
            var backoff = mock(AiRetryBackoff.class);
            assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                    .isInstanceOf(AiOutputValidationException.class);
            verifyNoInteractions(backoff);
        }
        verify(openAiGateway).execute(anyString(), any(), eq(OpenAiGenerationOutput.class));
        verify(geminiGateway).execute(anyString(), any(), eq(GeneratedPlanResponse.class));
    }

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void successfulFirstAttemptDoesNotRetry() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenReturn(validPlan());

        assertThat(service(client, backoff, 3).generatePlan(snapshot(), List.of())).isEqualTo(validPlan());

        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void invalidOutputIsNotRetried() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenReturn(emptyPlan(), validPlan());

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiOutputValidationException.class);

        ArgumentCaptor<AiGenerationRequest> requests = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(client).generatePlan(requests.capture());
        assertThat(requests.getAllValues().get(0).previousValidationIssues()).isEmpty();
        verifyNoInteractions(backoff);
    }

    @Test
    void parsingFailureFromProviderIsNotRetried() {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        var failure = new AiOutputValidationException("Antwort konnte nicht deserialisiert werden");
        when(client.generatePlan(any())).thenThrow(failure).thenReturn(validPlan());

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isSameAs(failure)
                .isInstanceOfSatisfying(AiOutputValidationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.INVALID_AI_RESPONSE));
        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void configuredAttemptLimitIncludesInitialCall() {
        AiClient client = mock(AiClient.class);
        when(client.generatePlan(any())).thenThrow(
                technical(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                        "vorübergehend nicht erreichbar"));

        assertThatThrownBy(() -> service(client, mock(AiRetryBackoff.class), 3)
                .generatePlan(snapshot(), List.of()))
                .isInstanceOfSatisfying(AiTechnicalException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE));
        verify(client, times(3)).generatePlan(any());
    }

    @Test
    void nonRetryableConfigurationFailureIsNotRetried() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenThrow(
                technical(AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, "API-Key falsch"));

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiTechnicalException.class);
        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void refusalIsNotRetried() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenThrow(
                technical(AiTechnicalErrorCode.AI_REFUSAL, "Anfrage abgelehnt"));

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiTechnicalException.class);
        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void temporaryTechnicalFailureThenSuccessUsesBackoff() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any()))
                .thenThrow(technical(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                        "vorübergehend nicht erreichbar"))
                .thenReturn(validPlan());

        assertThat(service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isEqualTo(validPlan());

        verify(client, times(2)).generatePlan(any());
        verify(backoff).waitBeforeRetry(1);
    }

    @Test
    void validationFailureStopsBeforeAnyLaterTechnicalRetry() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any()))
                .thenReturn(emptyPlan())
                .thenThrow(technical(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                        "vorübergehend nicht erreichbar"))
                .thenReturn(emptyPlan());

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiOutputValidationException.class);

        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void interruptedBackoffRestoresInterruptAndUsesStableErrorCode() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenThrow(
                technical(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE,
                        "vorübergehend nicht erreichbar"));
        doThrow(new InterruptedException("unterbrochen")).when(backoff).waitBeforeRetry(1);

        assertThatThrownBy(() -> service(client, backoff, 3).generatePlan(snapshot(), List.of()))
                .isInstanceOfSatisfying(AiTechnicalException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.RETRY_INTERRUPTED);
                    assertThat(AiTechnicalError
                            .from(exception, AiOperation.PLAN_GENERATION).isRetryable()).isFalse();
                });
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(client).generatePlan(any());
    }

    private AiPlanGenerationService service(AiClient client, AiRetryBackoff backoff, int maxAttempts) {
        AiExecutionProperties properties = new AiExecutionProperties();
        properties.setMaxAttempts(maxAttempts);
        return new AiPlanGenerationService(client, new GenerationResponseValidator(
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()), properties, backoff);
    }

    private AiTechnicalException technical(AiTechnicalErrorCode code, String message) {
        return new AiTechnicalException(code, message);
    }

    private GeneratedPlanResponse emptyPlan() {
        return new GeneratedPlanResponse(List.of());
    }

    private GeneratedPlanResponse validPlan() {
        return new GeneratedPlanResponse(
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null, null, null, 1,
                        List.of(
                                task("task-1", 1), task("task-2", 2), task("task-3", 3)),
                        List.of())));
    }

    private GeneratedTask task(String id, int order) {
        return new GeneratedTask(id, "Aufgabe " + order, null, 1, null, null, null,
                GeneratedElementOrigin.AI_INFERRED, order);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }
}

package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiClientTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiProviderConfigurationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.generation.*;
import de.melinadanhier.projectflow.ai.validation.generation.GenerationResponseValidator;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.plan.AiPlanGenerationService;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiPlanGenerationServiceTest {

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Test
    void successfulFirstAttemptDoesNotRetry() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenReturn(validPlan());

        assertThat(service(client, backoff, 2).generatePlan(snapshot(), List.of())).isEqualTo(validPlan());

        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void invalidOutputIsRetriedWithCodesAndMessages() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenReturn(emptyPlan(), validPlan());

        assertThat(service(client, backoff, 2).generatePlan(snapshot(), List.of()).phases()).hasSize(1);

        ArgumentCaptor<AiGenerationRequest> requests = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(client, times(2)).generatePlan(requests.capture());
        assertThat(requests.getAllValues().get(0).previousValidationIssues()).isEmpty();
        assertThat(requests.getAllValues().get(1).previousValidationIssues())
                .contains("PHASE_MISSING: Es wurde keine Phase erzeugt.",
                        "TASK_MISSING: Es wurde keine Aufgabe erzeugt.");
        verifyNoInteractions(backoff);
    }

    @Test
    void outputRetryLimitMeansInitialAttemptPlusConfiguredRetries() {
        AiClient client = mock(AiClient.class);
        when(client.generatePlan(any())).thenReturn(emptyPlan());

        assertThatThrownBy(() -> service(client, mock(AiRetryBackoff.class), 2)
                .generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiOutputValidationException.class);
        verify(client, times(3)).generatePlan(any());
    }

    @Test
    void permanentConfigurationFailureIsNotRetried() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenThrow(new AiProviderConfigurationException("API-Key falsch"));

        assertThatThrownBy(() -> service(client, backoff, 2).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiProviderConfigurationException.class);
        verify(client).generatePlan(any());
        verifyNoInteractions(backoff);
    }

    @Test
    void temporaryTechnicalFailureThenSuccessUsesBackoff() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any()))
                .thenThrow(new de.melinadanhier.projectflow.ai.exception.AiProviderUnavailableException(
                        "vorübergehend nicht erreichbar"))
                .thenReturn(validPlan());

        assertThat(service(client, backoff, 2).generatePlan(snapshot(), List.of()))
                .isEqualTo(validPlan());

        verify(client, times(2)).generatePlan(any());
        verify(backoff).waitBeforeRetry(1);
    }

    @Test
    void technicalAndValidationErrorsShareOneAttemptBudget() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any()))
                .thenReturn(emptyPlan())
                .thenThrow(new de.melinadanhier.projectflow.ai.exception.AiProviderUnavailableException(
                        "vorübergehend nicht erreichbar"))
                .thenReturn(emptyPlan());

        assertThatThrownBy(() -> service(client, backoff, 2).generatePlan(snapshot(), List.of()))
                .isInstanceOf(AiOutputValidationException.class);

        verify(client, times(3)).generatePlan(any());
        verify(backoff).waitBeforeRetry(2);
        verifyNoMoreInteractions(backoff);
    }

    @Test
    void interruptedBackoffRestoresInterruptAndUsesStableErrorCode() throws Exception {
        AiClient client = mock(AiClient.class);
        AiRetryBackoff backoff = mock(AiRetryBackoff.class);
        when(client.generatePlan(any())).thenThrow(
                new de.melinadanhier.projectflow.ai.exception.AiProviderUnavailableException(
                        "vorübergehend nicht erreichbar"));
        doThrow(new InterruptedException("unterbrochen")).when(backoff).waitBeforeRetry(1);

        assertThatThrownBy(() -> service(client, backoff, 2).generatePlan(snapshot(), List.of()))
                .isInstanceOfSatisfying(AiClientTechnicalException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AiTechnicalErrorCode.RETRY_INTERRUPTED);
                    assertThat(exception.isRetryable()).isFalse();
                });
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(client).generatePlan(any());
    }

    private AiPlanGenerationService service(AiClient client, AiRetryBackoff backoff, int retries) {
        AiExecutionProperties properties = new AiExecutionProperties();
        properties.setMaxAutomaticRetries(retries);
        return new AiPlanGenerationService(client, new GenerationResponseValidator(
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()), properties, backoff);
    }

    private GeneratedPlanResponse emptyPlan() {
        return new GeneratedPlanResponse(new GeneratedPlanMetadata("Plan", List.of()), List.of());
    }

    private GeneratedPlanResponse validPlan() {
        return new GeneratedPlanResponse(
                new GeneratedPlanMetadata("Plan", List.of()),
                List.of(new GeneratedPhase(
                        "phase-1", "Phase", null, null, null, 1,
                        List.of(new GeneratedTask(
                                "task-1", "Aufgabe", null, 1, null, null, null,
                                GeneratedElementOrigin.AI_INFERRED, 1)), List.of())));
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Projekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }
}

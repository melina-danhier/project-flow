package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalException;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckRequest;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.ai.validation.precheck.PreCheckResultValidator;
import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.service.coordination.AiPlanGenerationCoordinator;
import de.melinadanhier.projectflow.generation.service.retry.AiRetryBackoff;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckProcessor;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPreCheckProcessorTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private AiPreCheckWorkflowService workflowService;

    @Mock
    private AiRetryBackoff backoff;

    @Mock
    private AiPlanGenerationCoordinator generationCoordinator;

    @Mock
    private AiExecutionProperties executionProperties;

    @Mock
    private PreCheckResultValidator resultValidator;

    @InjectMocks
    private AiPreCheckProcessor processor;

    @Test
    void snapshotReadFailureIsPersistedAsDefinedTechnicalFailure() {
        UUID workflowId = UUID.randomUUID();
        when(workflowService.claimAndReadSnapshot(workflowId))
                .thenThrow(new GenerationException("Snapshot enthält unerwartete Daten"));

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowService).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.UNKNOWN_AI_ERROR));
        verifyNoInteractions(aiClient, backoff);
    }

    @Test
    void technicalProviderFailuresUseLimitedRetriesAndBackoff() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(executionProperties.getMaxAttempts()).thenReturn(3);
        when(workflowService.recordRetry(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE)))
                .thenReturn(java.util.OptionalInt.of(1), java.util.OptionalInt.of(2));
        when(aiClient.preCheck(request))
                .thenThrow(new AiTechnicalException(
                        AiTechnicalErrorCode.PROVIDER_UNAVAILABLE, "Provider nicht erreichbar"));

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(aiClient, times(3)).preCheck(request);
        verify(workflowService, times(2)).recordRetry(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE));
        verify(backoff).waitBeforeRetry(1);
        verify(backoff).waitBeforeRetry(2);
        verify(workflowService).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE));
    }

    @Test
    void plausibilityIssuesAreStoredWithoutRetryOrBackoff() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        AiPreCheckResult result = new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Zeitraum ist knapp", "Mehr Zeit einplanen")));
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(aiClient.preCheck(request)).thenReturn(result);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(resultValidator).validate(result);
        verify(workflowService).recordResult(workflowId, result);
        verify(workflowService, never()).recordRetry(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.PROVIDER_UNAVAILABLE));
        verify(workflowService, never()).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(backoff);
        verify(aiClient).preCheck(request);
        verifyNoInteractions(generationCoordinator);
    }

    @Test
    void resultWithoutProblemsStartsGenerationImmediately() {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckResult result = AiPreCheckResult.withoutIssues();
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(aiClient.preCheck(new AiPreCheckRequest(snapshot))).thenReturn(result);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowService).recordResult(workflowId, result);
        verifyNoInteractions(generationCoordinator);
    }

    @Test
    void invalidAiOutputIsNotRetriedAndIsNeverStoredAsBusinessError() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(aiClient.preCheck(org.mockito.ArgumentMatchers.any(AiPreCheckRequest.class)))
                .thenThrow(new AiOutputValidationException("Ungültiger Output"));
        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(AiPreCheckRequest.class);
        verify(aiClient).preCheck(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().previousValidationIssues()).isEmpty();
        verify(workflowService, never()).recordRetry(
                org.mockito.ArgumentMatchers.eq(workflowId), org.mockito.ArgumentMatchers.any());
        verify(workflowService).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.INVALID_AI_RESPONSE));
        verifyNoInteractions(backoff);
        verify(workflowService, never()).recordResult(
                org.mockito.ArgumentMatchers.eq(workflowId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unexpectedStatusUpdateFailureEndsInDefinedTechnicalState() {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        AiPreCheckResult result = AiPreCheckResult.withoutIssues();
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(aiClient.preCheck(request)).thenReturn(result);
        doThrow(new IllegalStateException("Status konnte nicht gespeichert werden"))
                .when(workflowService).recordResult(workflowId, result);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowService).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.UNKNOWN_AI_ERROR));
    }

    @Test
    void nonRetryableClientConfigurationFailureIsNotRetried() {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        when(workflowService.claimAndReadSnapshot(workflowId)).thenReturn(Optional.of(snapshot));
        when(aiClient.preCheck(new AiPreCheckRequest(snapshot)))
                .thenThrow(new AiTechnicalException(
                        AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR, "API-Key ungültig"));

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(aiClient).preCheck(new AiPreCheckRequest(snapshot));
        verifyNoInteractions(backoff);
        verify(workflowService).recordFailure(
                org.mockito.ArgumentMatchers.eq(workflowId), error(AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR));
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Testprojekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }

    private AiTechnicalError error(AiTechnicalErrorCode errorCode) {
        return org.mockito.ArgumentMatchers.argThat(error ->
                error.errorCode() == errorCode && error.operation() == AiOperation.PRE_CHECK);
    }
}

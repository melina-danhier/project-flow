package de.melinadanhier.projectflow.generation;

import de.melinadanhier.projectflow.common.exception.GenerationException;
import de.melinadanhier.projectflow.generation.client.AiClient;
import de.melinadanhier.projectflow.generation.client.AiOutputValidationException;
import de.melinadanhier.projectflow.generation.client.AiProviderUnavailableException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.request.AiPreCheckRequest;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.AiPreCheckErrorCode;
import de.melinadanhier.projectflow.generation.prompt.AiSchemaVersions;
import de.melinadanhier.projectflow.generation.service.AiPreCheckBackoff;
import de.melinadanhier.projectflow.generation.service.AiPreCheckProcessor;
import de.melinadanhier.projectflow.generation.service.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.service.AiWorkflowStateService;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

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
    private AiWorkflowStateService workflowStateService;

    @Mock
    private AiPreCheckBackoff backoff;

    @InjectMocks
    private AiPreCheckProcessor processor;

    @Test
    void snapshotReadFailureIsPersistedAsDefinedTechnicalFailure() {
        UUID workflowId = UUID.randomUUID();
        when(workflowStateService.markRunningAndReadSnapshot(workflowId))
                .thenThrow(new GenerationException("Snapshot enthält unerwartete Daten"));

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowStateService).recordTechnicalFailure(
                workflowId, AiPreCheckErrorCode.PRE_CHECK_INITIALIZATION_FAILED);
        verifyNoInteractions(aiClient, backoff);
    }

    @Test
    void technicalProviderFailuresUseLimitedRetriesAndBackoff() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        when(workflowStateService.markRunningAndReadSnapshot(workflowId)).thenReturn(snapshot);
        when(workflowStateService.recordAutomaticRetry(
                workflowId, AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE))
                .thenReturn(1, 2);
        when(aiClient.preCheck(request))
                .thenThrow(new AiProviderUnavailableException("Provider nicht erreichbar"));

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(aiClient, times(3)).preCheck(request);
        verify(workflowStateService, times(2)).recordAutomaticRetry(
                workflowId, AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE);
        verify(backoff).waitBeforeRetry(1);
        verify(backoff).waitBeforeRetry(2);
        verify(workflowStateService).recordTechnicalFailure(
                workflowId, AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    @Test
    void plausibilityIssuesAreStoredWithoutRetryOrBackoff() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        AiPreCheckResult result = new AiPreCheckResult(AiSchemaVersions.PRE_CHECK, List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Zeitraum ist knapp", "Mehr Zeit einplanen")));
        when(workflowStateService.markRunningAndReadSnapshot(workflowId)).thenReturn(snapshot);
        when(aiClient.preCheck(request)).thenReturn(result);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowStateService).recordResult(workflowId, result);
        verify(workflowStateService, never()).recordAutomaticRetry(
                workflowId, AiPreCheckErrorCode.AI_PROVIDER_UNAVAILABLE);
        verify(workflowStateService, never()).recordTechnicalFailure(
                workflowId, AiPreCheckErrorCode.PRE_CHECK_PROCESSING_FAILED);
        verifyNoInteractions(backoff);
        verify(aiClient).preCheck(request);
    }

    @Test
    void invalidAiOutputUsesLimitedTechnicalRetriesAndIsNeverStoredAsBusinessError() throws Exception {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        when(workflowStateService.markRunningAndReadSnapshot(workflowId)).thenReturn(snapshot);
        when(aiClient.preCheck(request))
                .thenThrow(new AiOutputValidationException("Ungültiger Output"));
        when(workflowStateService.recordAutomaticRetry(workflowId, AiPreCheckErrorCode.AI_OUTPUT_INVALID))
                .thenReturn(1, 2);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(aiClient, times(3)).preCheck(request);
        verify(workflowStateService, times(2)).recordAutomaticRetry(
                workflowId, AiPreCheckErrorCode.AI_OUTPUT_INVALID);
        verify(workflowStateService).recordTechnicalFailure(
                workflowId, AiPreCheckErrorCode.AI_OUTPUT_INVALID);
        verify(workflowStateService, never()).recordResult(
                org.mockito.ArgumentMatchers.eq(workflowId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unexpectedStatusUpdateFailureEndsInDefinedTechnicalState() {
        UUID workflowId = UUID.randomUUID();
        AiWizardSnapshot snapshot = snapshot();
        AiPreCheckRequest request = new AiPreCheckRequest(snapshot);
        AiPreCheckResult result = AiPreCheckResult.withoutIssues();
        when(workflowStateService.markRunningAndReadSnapshot(workflowId)).thenReturn(snapshot);
        when(aiClient.preCheck(request)).thenReturn(result);
        doThrow(new IllegalStateException("Status konnte nicht gespeichert werden"))
                .when(workflowStateService).recordResult(workflowId, result);

        processor.startAfterCommit(new AiPreCheckRequestedEvent(workflowId));

        verify(workflowStateService).recordTechnicalFailure(
                workflowId, AiPreCheckErrorCode.PRE_CHECK_PROCESSING_FAILED);
    }

    private AiWizardSnapshot snapshot() {
        return new AiWizardSnapshot(
                "Testprojekt", null, null, null,
                CollaborationMode.INDIVIDUAL, TemplateCategory.OTHER, "Test",
                null, null, null);
    }
}

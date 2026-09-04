package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.event.AiPreCheckRequestedEvent;
import de.melinadanhier.projectflow.generation.event.listener.AiGenerationRequestedEventListener;
import de.melinadanhier.projectflow.generation.event.listener.AiPreCheckRequestedEventListener;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import de.melinadanhier.projectflow.generation.service.coordination.AiPlanGenerationCoordinator;
import de.melinadanhier.projectflow.generation.service.precheck.AiPreCheckProcessor;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRequestedEventListenerTest {

    @Mock AiGenerationWorkflowService generationWorkflows;
    @Mock AiPlanGenerationCoordinator generationCoordinator;
    @Mock AiPreCheckWorkflowService preCheckWorkflows;
    @Mock AiPreCheckProcessor preCheckProcessor;

    @Test
    void generationListenerProcessesOnlySuccessfullyClaimedRun() {
        UUID workflowId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var work = new AiGenerationWork(
                workflowId, runId, null, List.of(), List.of(), List.of(), 0);
        when(generationWorkflows.claimWork(workflowId, runId)).thenReturn(Optional.of(work));
        var listener = new AiGenerationRequestedEventListener(generationWorkflows, generationCoordinator);

        listener.onGenerationRequested(new AiGenerationRequestedEvent(workflowId, runId));

        verify(generationCoordinator).generateClaimed(work);
    }

    @Test
    void staleGenerationRunIsNotProcessed() {
        UUID workflowId = UUID.randomUUID();
        UUID staleRunId = UUID.randomUUID();
        var listener = new AiGenerationRequestedEventListener(generationWorkflows, generationCoordinator);

        listener.onGenerationRequested(new AiGenerationRequestedEvent(workflowId, staleRunId));

        verify(generationWorkflows).claimWork(workflowId, staleRunId);
        verify(generationCoordinator, never()).generateClaimed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preCheckListenerProcessesOnlySuccessfullyClaimedRun() {
        UUID workflowId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AiWizardSnapshot snapshot = org.mockito.Mockito.mock(AiWizardSnapshot.class);
        when(preCheckWorkflows.claimAndReadSnapshot(workflowId, runId)).thenReturn(Optional.of(snapshot));
        var listener = new AiPreCheckRequestedEventListener(preCheckWorkflows, preCheckProcessor);

        listener.onPreCheckRequested(new AiPreCheckRequestedEvent(workflowId, runId));

        verify(preCheckProcessor).processClaimed(workflowId, runId, snapshot);
    }

    @Test
    void stalePreCheckRunIsNotProcessed() {
        UUID workflowId = UUID.randomUUID();
        UUID staleRunId = UUID.randomUUID();
        var listener = new AiPreCheckRequestedEventListener(preCheckWorkflows, preCheckProcessor);

        listener.onPreCheckRequested(new AiPreCheckRequestedEvent(workflowId, staleRunId));

        verify(preCheckWorkflows).claimAndReadSnapshot(workflowId, staleRunId);
        verify(preCheckProcessor, never()).processClaimed(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}

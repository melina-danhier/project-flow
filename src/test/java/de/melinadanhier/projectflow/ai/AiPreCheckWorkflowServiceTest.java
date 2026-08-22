package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiPreCheckWorkflowService;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPreCheckWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock AiPlanGenerationWorkflowRepository workflowRepository;
    @Mock AiWorkflowPayloadCodec payloadCodec;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PlanDraftMaterializationService materializationService;
    @Mock AiPlanGenerationWorkflow workflow;
    @Mock AiWizardSnapshot snapshot;

    @Test
    void claimUsesInjectedClockAndReadsSnapshotOnlyAfterAtomicClaim() {
        UUID workflowId = UUID.randomUUID();
        when(workflowRepository.claimPreCheck(workflowId, NOW)).thenReturn(1);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getConfirmedSnapshot()).thenReturn("{}");
        when(payloadCodec.readSnapshot("{}")).thenReturn(snapshot);

        var service = service();

        assertThat(service.claimAndReadSnapshot(workflowId)).contains(snapshot);
        verify(workflow).clearTechnicalError();
        verify(workflowRepository).claimPreCheck(workflowId, NOW);
    }

    @Test
    void stalePreCheckResultCannotOverwriteCurrentState() {
        UUID workflowId = UUID.randomUUID();
        AiPreCheckResult result = AiPreCheckResult.withoutIssues();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.PRE_CHECK_PENDING);

        assertThat(service().recordResult(workflowId, result)).isFalse();

        verify(workflow, never()).recordPreCheckResult(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(payloadCodec, eventPublisher);
    }

    @Test
    void resultWithoutProblemsMovesToGenerationAndPublishesEvent() {
        UUID workflowId = UUID.randomUUID();
        AiPreCheckResult result = AiPreCheckResult.withoutIssues();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        when(payloadCodec.writePreCheckResult(result)).thenReturn("{}");

        assertThat(service().recordResult(workflowId, result)).isTrue();

        verify(workflow).recordPreCheckResult("{}", false);
        verify(eventPublisher).publishEvent(new AiGenerationRequestedEvent(workflowId));
    }

    @Test
    void warningRequiresReviewAndDoesNotPublishGenerationEvent() {
        UUID workflowId = UUID.randomUUID();
        AiPreCheckResult result = new AiPreCheckResult(List.of(
                new AiPreCheckProblem(AiPreCheckSeverity.WARNING, "Knapp", "Mehr Zeit")));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        when(payloadCodec.writePreCheckResult(result)).thenReturn("{}");

        assertThat(service().recordResult(workflowId, result)).isTrue();

        verify(workflow).recordPreCheckResult("{}", true);
        verifyNoInteractions(eventPublisher);
    }

    private AiPreCheckWorkflowService service() {
        return new AiPreCheckWorkflowService(
                workflowRepository, payloadCodec, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

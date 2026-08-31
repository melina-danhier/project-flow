package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiWorkflowControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiWorkflowControlServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    @Mock AiPlanGenerationWorkflowRepository repository;
    @Mock AiWorkflowPayloadCodec codec;
    @Mock ApplicationEventPublisher events;
    @Mock AiPlanGenerationWorkflow workflow;
    @Mock AiWizardSnapshot snapshot;

    @Test
    void cancelsRunningPreCheckAndReturnsSnapshot() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID(), runId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getActiveRunId()).thenReturn(runId);
        when(workflow.cancelPreCheckRun(runId)).thenReturn(true);
        when(workflow.getConfirmedSnapshot()).thenReturn("{}");
        when(codec.readSnapshot("{}")).thenReturn(snapshot);

        var result = service().cancel(workflowId, userId);

        assertThat(result.changed()).isTrue();
        assertThat(result.operation()).isEqualTo(AiOperation.PRE_CHECK);
        assertThat(result.snapshot()).isSameAs(snapshot);
        verify(workflow, never()).cancelGenerationRun(runId);
    }

    @Test
    void cancelsRunningGenerationWithoutInvalidatingPreCheck() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID(), runId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getActiveRunId()).thenReturn(runId);
        when(workflow.cancelGenerationRun(runId)).thenReturn(true);

        var result = service().cancel(workflowId, userId);

        assertThat(result.changed()).isTrue();
        assertThat(result.operation()).isEqualTo(AiOperation.PLAN_GENERATION);
        verifyNoInteractions(codec);
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED);

        assertThat(service().cancel(workflowId, userId).changed()).isFalse();
    }

    @Test
    void repeatedPreCheckCancellationStillReturnsSnapshotForSafeNavigation() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED);
        when(workflow.getConfirmedSnapshot()).thenReturn("{}");
        when(codec.readSnapshot("{}")).thenReturn(snapshot);

        var result = service().cancel(workflowId, userId);

        assertThat(result.changed()).isFalse();
        assertThat(result.operation()).isEqualTo(AiOperation.PRE_CHECK);
        assertThat(result.snapshot()).isSameAs(snapshot);
    }

    @Test
    void repeatedGenerationStartReusesActiveRunAndPublishesNothing() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID(), runId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        when(workflow.getActiveRunId()).thenReturn(runId);

        assertThat(service().startGeneration(workflowId, userId)).isEqualTo(runId);
        verifyNoInteractions(events);
    }

    @Test
    void explicitGenerationStartCreatesOneBoundedRun() {
        UUID workflowId = UUID.randomUUID(), userId = UUID.randomUUID();
        owned(workflowId, userId);
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED);

        UUID runId = service().startGeneration(workflowId, userId);

        verify(workflow).startGeneration(runId, NOW.plus(Duration.ofMinutes(5)));
        var event = ArgumentCaptor.forClass(AiGenerationRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new AiGenerationRequestedEvent(workflowId, runId));
    }

    private void owned(UUID workflowId, UUID userId) {
        when(repository.findOwnedByIdForUpdate(workflowId, userId)).thenReturn(Optional.of(workflow));
    }

    private AiWorkflowControlService service() {
        AiExecutionProperties properties = new AiExecutionProperties();
        properties.setMaxRunTime(Duration.ofMinutes(5));
        return new AiWorkflowControlService(repository, codec, properties, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

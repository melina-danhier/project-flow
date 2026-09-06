package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper.MappedDraft;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftMaterializationService;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGenerationWorkflowServiceTest {
    @Mock AiPlanGenerationWorkflowRepository workflowRepository;
    @Mock AiWorkflowPayloadCodec payloadCodec;
    @Mock
    DraftMaterializationService materializationService;
    @Mock GeneratedPlanDraftMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository projectRepository;
    @Mock
    DraftRepository draftRepository;
    @Mock de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService authorizationService;
    @Mock GeneratedPlanResponse result;

    @Test
    void recordsAttemptAndActualVersionsOnlyForAnActiveProviderCall() {
        UUID workflowId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var workflow = mock(de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow.class);
        when(workflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.isActiveRun(eq(runId), any(Instant.class), eq(
                de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_RUNNING)))
                .thenReturn(true);

        service().recordProviderCall(
                workflowId, runId, "generation-v1", "generation-schema-v1");

        verify(workflow).recordGenerationAttempt("generation-v1", "generation-schema-v1");
    }

    @Test
    void mapsBeforeOpeningStorageTransactionWithoutSerializingTheResponse() {
        UUID workflowId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        MappedDraft contents = new MappedDraft(List.of(), List.of());
        when(mapper.map(result)).thenReturn(contents);
        when(result.criticalAssumptions()).thenReturn(List.of());
        when(payloadCodec.writeGeneratedPlan(result)).thenReturn("{}");
        when(materializationService.materialize(workflowId, runId, contents, "{}", false)).thenReturn(true);

        assertThat(service().recordSuccess(workflowId, runId, result)).isTrue();

        var order = inOrder(mapper, materializationService);
        order.verify(mapper).map(result);
        order.verify(materializationService).materialize(workflowId, runId, contents, "{}", false);
        verifyNoInteractions(workflowRepository);
    }

    @Test
    void invalidMappingNeverEntersPersistence() {
        when(mapper.map(result)).thenThrow(new AiOutputValidationException("Mehrdeutige Referenz"));
        assertThatThrownBy(() -> service().recordSuccess(UUID.randomUUID(), UUID.randomUUID(), result))
                .isInstanceOf(AiOutputValidationException.class);
        verifyNoInteractions(workflowRepository, materializationService, payloadCodec);
    }

    @Test
    void storageFailurePropagatesToCoordinatorAfterTransactionRollback() {
        UUID workflowId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        MappedDraft contents = new MappedDraft(List.of(), List.of());
        when(mapper.map(result)).thenReturn(contents);
        when(result.criticalAssumptions()).thenReturn(List.of());
        when(payloadCodec.writeGeneratedPlan(result)).thenReturn("{}");
        when(materializationService.materialize(workflowId, runId, contents, "{}", false))
                .thenThrow(new IllegalStateException("Draft konnte nicht gespeichert werden"));
        assertThatThrownBy(() -> service().recordSuccess(workflowId, runId, result))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void technicalPreCheckFailureCannotBeRetriedAsGeneration() {
        UUID workflowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var workflow = mock(AiPlanGenerationWorkflow.class);
        when(workflowRepository.findOwnedByIdForUpdate(workflowId, userId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        when(workflow.getLastAiOperation()).thenReturn(AiOperation.PRE_CHECK);

        assertThatThrownBy(() -> service().retry(workflowId, userId))
                .isInstanceOf(ConflictException.class);

        verify(workflow, never()).startGeneration(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retryableGenerationFailureStartsNewRun() {
        UUID workflowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var workflow = mock(AiPlanGenerationWorkflow.class);
        when(workflowRepository.findOwnedByIdForUpdate(workflowId, userId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        when(workflow.getLastAiOperation()).thenReturn(AiOperation.PLAN_GENERATION);
        when(workflow.getLastErrorRetryable()).thenReturn(true);

        service().retry(workflowId, userId);

        var runId = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(workflow).startGeneration(runId.capture(), any(Instant.class));
        verify(eventPublisher).publishEvent(new AiGenerationRequestedEvent(workflowId, runId.getValue()));
    }

    @Test
    void draftRegenerationLocksProjectThenDraftThenWorkflow() {
        UUID projectId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var project = new de.melinadanhier.projectflow.plancontainer.project.model.Project();
        project.setStatus(de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus.DRAFT);
        project.setLocation(de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation.DRAFT);
        var draft = new de.melinadanhier.projectflow.draft.model.DraftPlan();
        draft.setProject(project);
        var workflow = de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow.create(
                project, "{}", "v1", UUID.randomUUID(), Instant.now(), "v1");
        ReflectionTestUtils.setField(draft, "id", draftId);
        ReflectionTestUtils.setField(workflow, "id", workflowId);
        ReflectionTestUtils.setField(workflow, "status",
                de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
        when(projectRepository.findForUpdate(projectId)).thenReturn(Optional.of(projectId));
        when(draftRepository.findForUpdateByProjectId(projectId)).thenReturn(Optional.of(draft));
        when(workflowRepository.findByProjectId(projectId)).thenReturn(Optional.of(workflow));
        when(workflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

        service().regenerateDraft(projectId, draftId, userId, draft.getLockVersion());

        var order = inOrder(projectRepository, draftRepository, workflowRepository);
        order.verify(projectRepository).findForUpdate(projectId);
        order.verify(draftRepository).findForUpdateByProjectId(projectId);
        order.verify(workflowRepository).findByProjectId(projectId);
        order.verify(workflowRepository).findByIdForUpdate(workflowId);
    }

    private AiGenerationWorkflowService service() {
        return new AiGenerationWorkflowService(
                workflowRepository, payloadCodec, materializationService, mapper, Clock.systemUTC(), eventPublisher,
                projectRepository, draftRepository, authorizationService);
    }
}

package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.exception.AiOutputValidationException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper.MappedDraft;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock PlanDraftMaterializationService materializationService;
    @Mock GeneratedPlanDraftMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository projectRepository;
    @Mock de.melinadanhier.projectflow.draft.repository.PlanDraftRepository planDraftRepository;
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
        MappedDraft contents = new MappedDraft(List.of(), List.of());
        when(mapper.map(result)).thenReturn(contents);
        when(result.criticalAssumptions()).thenReturn(List.of());
        when(payloadCodec.writeGeneratedPlan(result)).thenReturn("{}");
        when(materializationService.materialize(workflowId, contents, "{}", false)).thenReturn(true);

        assertThat(service().recordSuccess(workflowId, result)).isTrue();

        var order = inOrder(mapper, materializationService);
        order.verify(mapper).map(result);
        order.verify(materializationService).materialize(workflowId, contents, "{}", false);
        verifyNoInteractions(workflowRepository);
    }

    @Test
    void invalidMappingNeverEntersPersistence() {
        when(mapper.map(result)).thenThrow(new AiOutputValidationException("Mehrdeutige Referenz"));
        assertThatThrownBy(() -> service().recordSuccess(UUID.randomUUID(), result))
                .isInstanceOf(AiOutputValidationException.class);
        verifyNoInteractions(workflowRepository, materializationService, payloadCodec);
    }

    @Test
    void storageFailurePropagatesToCoordinatorAfterTransactionRollback() {
        UUID workflowId = UUID.randomUUID();
        MappedDraft contents = new MappedDraft(List.of(), List.of());
        when(mapper.map(result)).thenReturn(contents);
        when(result.criticalAssumptions()).thenReturn(List.of());
        when(payloadCodec.writeGeneratedPlan(result)).thenReturn("{}");
        when(materializationService.materialize(workflowId, contents, "{}", false))
                .thenThrow(new IllegalStateException("Draft konnte nicht gespeichert werden"));
        assertThatThrownBy(() -> service().recordSuccess(workflowId, result))
                .isInstanceOf(IllegalStateException.class);
    }

    private AiGenerationWorkflowService service() {
        return new AiGenerationWorkflowService(
                workflowRepository, payloadCodec, materializationService, mapper, Clock.systemUTC(), eventPublisher,
                projectRepository, planDraftRepository, authorizationService);
    }
}

package de.melinadanhier.projectflow.draft;

import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper.MappedDraft;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftMaterializationService;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDraftLockOrderingTest {

    @Mock private DraftRepository draftRepository;
    @Mock private AiPlanGenerationWorkflowRepository workflowRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private AiPlanGenerationWorkflow workflow;

    @Test
    void materializationLocksProjectThenDraftThenWorkflow() {
        UUID workflowId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(workflowRepository.findProjectIdById(workflowId)).thenReturn(Optional.of(projectId));
        when(projectRepository.findForUpdate(projectId)).thenReturn(Optional.of(projectId));
        when(draftRepository.findForUpdateByProjectId(projectId)).thenReturn(Optional.empty());
        when(workflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.isActiveRun(eq(runId), any(Instant.class),
                eq(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING))).thenReturn(false);
        DraftMaterializationService service = new DraftMaterializationService(
                draftRepository, workflowRepository, projectRepository, Clock.systemUTC());

        assertThat(service.materialize(
                workflowId, runId, new MappedDraft(List.of(), List.of()), "{}", false)).isFalse();

        InOrder order = inOrder(workflowRepository, projectRepository, draftRepository);
        order.verify(workflowRepository).findProjectIdById(workflowId);
        order.verify(projectRepository).findForUpdate(projectId);
        order.verify(draftRepository).findForUpdateByProjectId(projectId);
        order.verify(workflowRepository).findByIdForUpdate(workflowId);
    }
}

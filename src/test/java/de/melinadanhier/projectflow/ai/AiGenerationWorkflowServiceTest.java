package de.melinadanhier.projectflow.ai;

import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.generation.service.workflow.AiGenerationWorkflowService;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationWorkflowServiceTest {

    @Mock AiPlanGenerationWorkflowRepository workflowRepository;
    @Mock AiWorkflowPayloadCodec payloadCodec;
    @Mock PlanDraftMaterializationService materializationService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AiPlanGenerationWorkflow workflow;
    @Mock GeneratedPlanResponse result;
    @Mock Project project;

    @Test
    void staleGenerationResultCannotOverwriteCurrentState() {
        UUID workflowId = UUID.randomUUID();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);

        assertThat(service().recordSuccess(workflowId, result)).isFalse();

        verify(workflow, never()).recordGeneratedPlan(org.mockito.ArgumentMatchers.any());
        verify(materializationService, never()).materialize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedMaterializationDoesNotMarkGenerationCompleted() {
        UUID workflowId = UUID.randomUUID();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflow.getStatus()).thenReturn(AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
        when(workflow.getProject()).thenReturn(project);
        when(payloadCodec.writeGeneratedPlan(result)).thenReturn("{}");
        when(materializationService.materialize(project, result))
                .thenThrow(new IllegalStateException("Draft konnte nicht gespeichert werden"));

        assertThatThrownBy(() -> service().recordSuccess(workflowId, result))
                .isInstanceOf(IllegalStateException.class);

        verify(workflow, never()).recordGeneratedPlan("{}");
    }

    private AiGenerationWorkflowService service() {
        return new AiGenerationWorkflowService(
                workflowRepository, payloadCodec, materializationService, Clock.systemUTC(), eventPublisher);
    }
}

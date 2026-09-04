package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.response.AiWorkflowStatusDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiWorkflowQueryService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowControlService controlService;

    public AiWorkflowStatusDto getOwnedStatus(UUID workflowId, UUID userId) {
        workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        controlService.expire(workflowId);
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        return new AiWorkflowStatusDto(
                workflow.getId(), workflow.getProject().getId(),
                workflow.getStatus(), workflow.getPreCheckRetryCount(),
                workflow.getGenerationRoundAttemptCount(),
                workflow.getGenerationTotalAttemptCount(),
                workflow.getLastTechnicalError(),
                workflow.getLastAiOperation(),
                workflow.getLastErrorRetryable(),
                workflow.hasFailedAssumptionRegeneration());
    }
}

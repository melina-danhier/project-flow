package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.model.AiPreCheckErrorCode;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiWorkflowStateService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiSnapshotCodec snapshotCodec;

    @Transactional
    public AiWizardSnapshot markRunningAndReadSnapshot(UUID workflowId) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING);
        workflow.setLastTechnicalError(null);
        return snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
    }

    @Transactional
    public int recordAutomaticRetry(UUID workflowId, AiPreCheckErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setRetryCount(workflow.getRetryCount() + 1);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING);
        workflow.setLastTechnicalError(errorCode.name());
        return workflow.getRetryCount();
    }

    @Transactional
    public void recordResult(UUID workflowId, AiPreCheckResult result) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setPreCheckResult(snapshotCodec.writePreCheckResult(result));
        workflow.setLastTechnicalError(null);
        workflow.setStatus(result.hasPlausibilityIssues()
                ? AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                : AiPlanGenerationWorkflowStatus.PRE_CHECK_PASSED);
    }

    @Transactional
    public void recordTechnicalFailure(UUID workflowId, AiPreCheckErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        workflow.setLastTechnicalError(errorCode.name());
    }

    private AiPlanGenerationWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

}

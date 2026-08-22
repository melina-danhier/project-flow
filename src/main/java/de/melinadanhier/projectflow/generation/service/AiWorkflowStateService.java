package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.GeneratedPlanResponse;
import de.melinadanhier.projectflow.generation.model.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;

@Service
@RequiredArgsConstructor
public class AiWorkflowStateService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiSnapshotCodec snapshotCodec;
    private final DraftService draftService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<AiWizardSnapshot> claimPreCheckAndReadSnapshot(UUID workflowId) {
        if (workflowRepository.claimPreCheck(workflowId, Instant.now()) != 1) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setLastTechnicalError(null);
        return Optional.of(snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot()));
    }

    @Transactional
    public int recordAutomaticRetry(UUID workflowId, AiTechnicalErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setRetryCount(workflow.getRetryCount() + 1);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.PRE_CHECK_RETRY_PENDING);
        workflow.setLastTechnicalError(errorCode);
        return workflow.getRetryCount();
    }

    @Transactional
    public void recordResult(UUID workflowId, AiPreCheckResult result) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setPreCheckResult(snapshotCodec.writePreCheckResult(result));
        workflow.setLastTechnicalError(null);
        workflow.setStatus(result.hasPlausibilityIssues()
                ? AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                : AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        if (!result.hasPlausibilityIssues()) {
            eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        }
    }

    @Transactional
    public void recordTechnicalFailure(UUID workflowId, AiTechnicalErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE);
        workflow.setLastTechnicalError(errorCode);
    }

    @Transactional
    public boolean claimGeneration(UUID workflowId) {
        return workflowRepository.claimGeneration(workflowId, Instant.now()) == 1;
    }

    @Transactional
    public Optional<AiGenerationWork> claimGenerationWork(UUID workflowId) {
        if (!claimGeneration(workflowId)) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        AiPreCheckResult result = snapshotCodec.readPreCheckResult(workflow.getPreCheckResult());
        return Optional.of(new AiGenerationWork(
                workflowId,
                snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot()),
                result.problems().stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList()));
    }

    @Transactional
    public void recordGeneratedPlan(UUID workflowId, GeneratedPlanResponse result) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return;
        }
        draftService.materialize(workflow.getProject(), result);
        workflow.setGeneratedPlan(snapshotCodec.writeGeneratedPlan(result));
        workflow.setLastTechnicalError(null);
        workflow.setStatus(AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED);
    }

    @Transactional
    public void recordGenerationFailure(UUID workflowId, AiTechnicalErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return;
        }
        workflow.setStatus(AiPlanGenerationWorkflowStatus.GENERATION_FAILED);
        workflow.setLastTechnicalError(errorCode);
    }

    private AiPlanGenerationWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

}

package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPreCheckWorkflowService {
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Optional<AiWizardSnapshot> claimAndReadSnapshot(UUID workflowId) {
        if (workflowRepository.claimPreCheck(workflowId, Instant.now(clock)) != 1) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        workflow.clearTechnicalError();
        return Optional.of(payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()));
    }

    @Transactional(readOnly = true)
    public int getPreCheckRetryCount(UUID workflowId) {
        return require(workflowId).getPreCheckRetryCount();
    }

    @Transactional
    public OptionalInt recordRetry(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(workflow.recordPreCheckRetry(error));
    }

    @Transactional
    public boolean recordResult(UUID workflowId, AiPreCheckResult result) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING) {
            return false;
        }
        boolean needsReview = result.hasPlausibilityIssues();
        workflow.recordPreCheckResult(payloadCodec.writePreCheckResult(result), needsReview);
        if (!needsReview) {
            eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        }
        return true;
    }

    @Transactional
    public boolean recordFailure(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_RUNNING) {
            return false;
        }
        workflow.recordPreCheckFailure(error);
        return true;
    }

    private AiPlanGenerationWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }
}

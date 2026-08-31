package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.config.AiExecutionProperties;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.common.exception.ConflictException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiWorkflowControlService {
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final AiExecutionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public UUID startGeneration(UUID workflowId, UUID userId) {
        var workflow = requireOwnedForUpdate(workflowId, userId);
        expireIfNecessary(workflow);
        if (workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return workflow.getActiveRunId();
        }
        if (workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                || workflow.getStatus() == AiPlanGenerationWorkflowStatus.ASSUMPTIONS_REVIEW_PENDING) {
            return workflow.getActiveRunId();
        }
        Instant now = Instant.now(clock);
        UUID runId = UUID.randomUUID();
        try {
            workflow.startGeneration(runId, now.plus(properties.getMaxRunTime()));
        } catch (IllegalStateException exception) {
            throw new ConflictException(exception.getMessage());
        }
        events.publishEvent(new AiGenerationRequestedEvent(workflowId, runId));
        return runId;
    }

    @Transactional
    public Cancellation cancel(UUID workflowId, UUID userId) {
        var workflow = requireOwnedForUpdate(workflowId, userId);
        UUID runId = workflow.getActiveRunId();
        if (workflow.cancelPreCheckRun(runId)) {
            return new Cancellation(true, AiOperation.PRE_CHECK,
                    payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()));
        }
        if (workflow.cancelGenerationRun(runId)) {
            return new Cancellation(true, AiOperation.PLAN_GENERATION, null);
        }
        AiOperation operation = switch (workflow.getStatus()) {
            case PRE_CHECK_PENDING, PRE_CHECK_RUNNING, PRE_CHECK_RETRY_PENDING,
                    PRE_CHECK_SUCCEEDED, PRE_CHECK_NEEDS_REVIEW, PRE_CHECK_CANCELLED -> AiOperation.PRE_CHECK;
            default -> AiOperation.PLAN_GENERATION;
        };
        AiWizardSnapshot snapshot = workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_CANCELLED
                ? payloadCodec.readSnapshot(workflow.getConfirmedSnapshot())
                : null;
        return new Cancellation(false, operation, snapshot);
    }

    @Transactional
    public boolean expire(UUID workflowId) {
        var workflow = workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        return expireIfNecessary(workflow);
    }

    private boolean expireIfNecessary(AiPlanGenerationWorkflow workflow) {
        AiOperation operation = switch (workflow.getStatus()) {
            case PRE_CHECK_PENDING, PRE_CHECK_RUNNING, PRE_CHECK_RETRY_PENDING -> AiOperation.PRE_CHECK;
            default -> AiOperation.PLAN_GENERATION;
        };
        var timeout = new IllegalStateException("Die maximale Laufzeit der KI-Ausführung wurde überschritten.");
        return workflow.expire(workflow.getActiveRunId(), Instant.now(clock),
                new AiTechnicalError(AiTechnicalErrorCode.PROVIDER_TIMEOUT, operation,
                        timeout.getMessage(), timeout));
    }

    private AiPlanGenerationWorkflow requireOwnedForUpdate(UUID workflowId, UUID userId) {
        return workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

    public record Cancellation(boolean changed, AiOperation operation, AiWizardSnapshot snapshot) { }
}

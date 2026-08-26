package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiGenerationWorkflowService {
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final PlanDraftMaterializationService draftMaterializationService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<AiGenerationWork> claimWork(UUID workflowId) {
        if (workflowRepository.claimGeneration(workflowId, Instant.now(clock)) != 1) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        AiPreCheckResult result = payloadCodec.readPreCheckResult(workflow.getPreCheckResult());
        var acknowledgedIndices = workflow.getAcknowledgedWarningIndices();
        if (result.hasErrors()) {
            throw new IllegalStateException("Ein Workflow mit Pre-Check-Fehlern darf nicht generiert werden.");
        }
        for (int index = 0; index < result.problems().size(); index++) {
            if (result.problems().get(index).severity() == AiPreCheckSeverity.WARNING
                    && !acknowledgedIndices.contains(index)) {
                throw new IllegalStateException(
                        "Ein Workflow mit nicht akzeptierten Warnungen darf nicht generiert werden.");
            }
        }
        return Optional.of(new AiGenerationWork(
                workflowId,
                payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()),
                result.problems().stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList(),
                workflow.getGenerationRoundAttemptCount(),
                workflow.getGenerationPromptVersion()));
    }

    @Transactional
    public void recordProviderCall(UUID workflowId) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            throw new ConflictException("Die Generierung ist nicht mehr aktiv.");
        }
        workflow.recordGenerationAttempt();
    }

    @Transactional
    public boolean recordSuccess(UUID workflowId, GeneratedPlanResponse result) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        String serializedPlan = payloadCodec.writeGeneratedPlan(result);
        draftMaterializationService.materialize(workflow.getProject(), result);
        workflow.recordGeneratedPlan(serializedPlan);
        return true;
    }

    @Transactional
    public boolean recordGenerationFailure(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        workflow.recordGenerationFailure(error);
        return true;
    }

    @Transactional
    public boolean recordTechnicalFailure(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        workflow.recordTechnicalFailure(error);
        return true;
    }

    @Transactional
    public boolean retry(UUID workflowId, UUID userId) {
        if (workflowRepository.retryGeneration(workflowId, userId, Instant.now(clock)) != 1) {
            return false;
        }
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        return true;
    }

    /** Technical entry point after repairing the server-side AI client configuration; not exposed in the user UI. */
    @Transactional
    public boolean retryAfterAdministrativeFix(UUID workflowId) {
        if (workflowRepository.retryGenerationAfterClientConfigurationFix(
                workflowId, Instant.now(clock)) != 1) {
            return false;
        }
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        return true;
    }

    private AiPlanGenerationWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }
}

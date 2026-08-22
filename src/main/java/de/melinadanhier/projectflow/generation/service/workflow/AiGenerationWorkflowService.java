package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
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

    @Transactional
    public Optional<AiGenerationWork> claimWork(UUID workflowId) {
        if (workflowRepository.claimGeneration(workflowId, Instant.now(clock)) != 1) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        AiPreCheckResult result = payloadCodec.readPreCheckResult(workflow.getPreCheckResult());
        return Optional.of(new AiGenerationWork(
                workflowId,
                payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()),
                result.problems().stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList()));
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
    public boolean recordFailure(UUID workflowId, AiTechnicalErrorCode errorCode) {
        AiPlanGenerationWorkflow workflow = require(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        workflow.recordGenerationFailure(errorCode);
        return true;
    }

    private AiPlanGenerationWorkflow require(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }
}

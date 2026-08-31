package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.AiPreCheckProblemDto;
import de.melinadanhier.projectflow.generation.dto.AiPreCheckReviewDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPreCheckReviewService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec snapshotCodec;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public AiPreCheckReviewDto getReview(UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        List<AiPreCheckProblemDto> problems = new ArrayList<>();
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            problems.add(new AiPreCheckProblemDto(
                    index, problem.severity(), problem.message(), problem.suggestion(),
                    workflow.getAcknowledgedWarningIndices().contains(index)));
        }
        return new AiPreCheckReviewDto(workflowId, workflow.getProject().getId(), problems);
    }

    @Transactional
    public boolean acknowledgeWarning(UUID workflowId, UUID userId, int problemIndex) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            boolean alreadyAccepted = workflow.getAcknowledgedWarningIndices().contains(problemIndex);
            boolean generationStarted = workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED;
            if (alreadyAccepted && generationStarted) {
                return true;
            }
        }
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        List<AiPreCheckProblem> problems = result.problems();
        if (problemIndex < 0 || problemIndex >= problems.size()
                || problems.get(problemIndex).severity() != AiPreCheckSeverity.WARNING) {
            throw new ResourceNotFoundException("Die Warnung wurde nicht gefunden.");
        }
        workflow.acknowledgeWarning(problemIndex);
        if (result.hasErrors()) {
            return false;
        }
        boolean allAcknowledged = true;
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            if (problem.severity() == AiPreCheckSeverity.WARNING
                    && !workflow.getAcknowledgedWarningIndices().contains(index)) {
                allAcknowledged = false;
                break;
            }
        }
        if (!allAcknowledged) {
            return false;
        }
        workflow.approvePreCheck();
        return true;
    }

    @Transactional
    public de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot returnToWizard(
            UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        return snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
    }

    private AiPlanGenerationWorkflow requireOwned(UUID workflowId, UUID userId) {
        return workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

    private void requireReviewable(AiPlanGenerationWorkflow workflow) {
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_SUCCEEDED
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_CANCELLED) {
            throw new ConflictException("Für diesen KI-Workflow liegen keine aktuellen Hinweise vor.");
        }
    }

    private AiPreCheckResult readResult(AiPlanGenerationWorkflow workflow) {
        if (workflow.getPreCheckResult() == null) {
            throw new ConflictException("Das Ergebnis der KI-Prüfung liegt noch nicht vor.");
        }
        return snapshotCodec.readPreCheckResult(workflow.getPreCheckResult());
    }
}

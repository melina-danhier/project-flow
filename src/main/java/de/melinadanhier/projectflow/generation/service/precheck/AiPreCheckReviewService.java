package de.melinadanhier.projectflow.generation.service.precheck;

import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckProblem;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.precheck.AiPreCheckProblemDto;
import de.melinadanhier.projectflow.generation.dto.precheck.AiPreCheckReviewDto;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public AiPreCheckReviewDto getReview(UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        List<AiPreCheckProblemDto> problems = new ArrayList<>();
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            problems.add(new AiPreCheckProblemDto(
                    index, problem.severity(), problem.type(), problem.message(),
                    problem.suggestedUserAction(), problem.reviewQuestion(), problem.acceptedInterpretation(),
                    workflow.getAcceptedOpenPointIndices().contains(index)));
        }
        return new AiPreCheckReviewDto(workflowId, workflow.getProject().getId(), problems);
    }

    @Transactional
    public boolean acceptOpenPoint(UUID workflowId, UUID userId, int problemIndex) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            boolean alreadyAccepted = workflow.getAcceptedOpenPointIndices().contains(problemIndex);
            boolean generationStarted = workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED;
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
        workflow.acceptOpenPoint(problemIndex);
        return completeReviewIfPossible(workflow, result);
    }

    @Transactional
    public boolean confirmOpenPointContext(
            UUID workflowId, UUID userId, int problemIndex, String confirmedContext) {
        AiPlanGenerationWorkflow workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if (confirmedContext == null || confirmedContext.isBlank() || confirmedContext.trim().length() > 1000) {
            throw new DomainValidationException(
                    "Die eigene Planungsgrundlage muss zwischen 1 und 1000 Zeichen enthalten.");
        }
        String normalizedContext = confirmedContext.trim();
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
            boolean sameContextAlreadyConfirmed = workflow.getAcceptedOpenPointIndices().contains(problemIndex)
                    && normalizedContext.equals(
                            workflow.getCustomOpenPointInterpretations().get(problemIndex));
            boolean generationStarted = workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_PENDING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_RUNNING
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED
                    || workflow.getStatus() == AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED;
            if (sameContextAlreadyConfirmed && generationStarted) {
                return true;
            }
        }
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        requireOpenPoint(result, problemIndex);
        workflow.confirmOpenPointContext(problemIndex, normalizedContext);
        return completeReviewIfPossible(workflow, result);
    }

    private boolean completeReviewIfPossible(
            AiPlanGenerationWorkflow workflow, AiPreCheckResult result) {
        if (result.hasErrors()) {
            return false;
        }
        boolean allAcknowledged = true;
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            if (problem.severity() == AiPreCheckSeverity.WARNING
                    && !workflow.getAcceptedOpenPointIndices().contains(index)) {
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

    private void requireOpenPoint(AiPreCheckResult result, int problemIndex) {
        List<AiPreCheckProblem> problems = result.problems();
        if (problemIndex < 0 || problemIndex >= problems.size()
                || problems.get(problemIndex).severity() != AiPreCheckSeverity.WARNING) {
            throw new ResourceNotFoundException("Der offene Punkt wurde nicht gefunden.");
        }
    }

    @Transactional
    public de.melinadanhier.projectflow.generation.model.wizard.AiWizardSnapshot returnToWizard(
            UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        if (workflow.getConfirmedSnapshot() == null) {
            throw new ConflictException("Für diesen KI-Workflow liegen keine gespeicherten Eingaben vor.");
        }
        return snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
    }

    private AiPlanGenerationWorkflow requireOwned(UUID workflowId, UUID userId) {
        return workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

    private void requireReviewable(AiPlanGenerationWorkflow workflow) {
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_COMPLETED
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

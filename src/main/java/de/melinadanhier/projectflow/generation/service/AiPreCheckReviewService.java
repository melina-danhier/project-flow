package de.melinadanhier.projectflow.generation.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.generation.dto.request.AiWizardSnapshot;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblem;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckProblemView;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckResult;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckReviewDto;
import de.melinadanhier.projectflow.generation.dto.response.AiPreCheckSeverity;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPreCheckReviewService {

    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiSnapshotCodec snapshotCodec;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public AiPreCheckReviewDto getReview(UUID workflowId, UUID userId, Set<Integer> ignoredWarnings) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        List<AiPreCheckProblemView> visibleProblems = new ArrayList<>();
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            if (problem.severity() == AiPreCheckSeverity.WARNING && ignoredWarnings.contains(index)) {
                continue;
            }
            visibleProblems.add(new AiPreCheckProblemView(
                    index, problem.severity(), problem.message(), problem.suggestion()));
        }
        return new AiPreCheckReviewDto(workflowId, workflow.getProject().getId(), visibleProblems);
    }

    @Transactional(readOnly = true)
    public void requireIgnorableWarning(UUID workflowId, UUID userId, int problemIndex) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        List<AiPreCheckProblem> problems = readResult(workflow).problems();
        if (problemIndex < 0 || problemIndex >= problems.size()
                || problems.get(problemIndex).severity() != AiPreCheckSeverity.WARNING) {
            throw new ResourceNotFoundException("Die Warnung wurde nicht gefunden.");
        }
    }

    @Transactional
    public AiGenerationPreparation prepareGeneration(
            UUID workflowId,
            UUID userId,
            Set<Integer> ignoredWarningIndices
    ) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        AiPreCheckResult result = readResult(workflow);
        if (result.hasErrors()) {
            throw new ConflictException("Solange Fehler vorliegen, kann kein Plan generiert werden.");
        }
        List<AiPreCheckProblem> warnings = new ArrayList<>();
        for (int index = 0; index < result.problems().size(); index++) {
            AiPreCheckProblem problem = result.problems().get(index);
            if (problem.severity() == AiPreCheckSeverity.WARNING) {
                if (!ignoredWarningIndices.contains(index)) {
                    throw new ConflictException("Bitte prüfe zuerst alle Warnungen.");
                }
                warnings.add(problem);
            }
        }
        workflow.setStatus(AiPlanGenerationWorkflowStatus.GENERATION_PENDING);
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId));
        return new AiGenerationPreparation(
                workflowId,
                snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot()),
                List.copyOf(warnings));
    }

    @Transactional
    public AiWizardSnapshot returnToWizard(UUID workflowId, UUID userId) {
        AiPlanGenerationWorkflow workflow = requireOwned(workflowId, userId);
        requireReviewable(workflow);
        return snapshotCodec.readSnapshot(workflow.getConfirmedSnapshot());
    }

    private AiPlanGenerationWorkflow requireOwned(UUID workflowId, UUID userId) {
        return workflowRepository.findOwnedById(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

    private void requireReviewable(AiPlanGenerationWorkflow workflow) {
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.PRE_CHECK_NEEDS_REVIEW) {
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

package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.service.PlanDraftMaterializationService;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
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
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.draft.service.DraftVersionConflictException;

@Service
@RequiredArgsConstructor
public class AiGenerationWorkflowService {
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final PlanDraftMaterializationService draftMaterializationService;
    private final GeneratedPlanDraftMapper draftMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectRepository projectRepository;
    private final PlanDraftRepository planDraftRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public Optional<AiGenerationWork> claimWork(UUID workflowId) {
        if (workflowRepository.claimGeneration(workflowId, Instant.now(clock)) != 1) {
            return Optional.empty();
        }
        AiPlanGenerationWorkflow workflow = require(workflowId);
        AiPreCheckResult result = payloadCodec.readPreCheckResult(workflow.getPreCheckResult());
        var acknowledgedIndices = workflow.getAcknowledgedWarningIndices();
        var assumptionContext = payloadCodec.readAssumptionContext(workflow.getGenerationAssumptionContext());
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
                assumptionContext.confirmedAssumptions(),
                assumptionContext.rejectedAssumptions(),
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean recordSuccess(UUID workflowId, GeneratedPlanResponse result) {
        var contents = draftMapper.map(result);
        return draftMaterializationService.materialize(
                workflowId, contents, payloadCodec.writeGeneratedPlan(result),
                !result.criticalAssumptions().isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordGenerationFailure(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = requireForUpdate(workflowId);
        if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_RUNNING) {
            return false;
        }
        workflow.recordGenerationFailure(error);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordTechnicalFailure(UUID workflowId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = requireForUpdate(workflowId);
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

    @Transactional
    public UUID regenerateDraft(UUID projectId, UUID draftId, UUID userId, long lockVersion) {
        projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt oder Ressource wurde nicht gefunden."));
        authorizationService.requireOwner(projectId, userId);
        var draft = planDraftRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Planentwurf nicht gefunden."));
        if (!draftId.equals(draft.getId()) || draft.getLockVersion() != lockVersion) {
            throw new DraftVersionConflictException(
                    "Der Entwurf wurde zwischenzeitlich geändert. Bitte prüfe ihn erneut.");
        }
        if (draft.getStatus() != DraftPlanStatus.READY_FOR_REVIEW
                && draft.getStatus() != DraftPlanStatus.IN_REVIEW) {
            throw new ConflictException("Der Entwurf kann in diesem Zustand nicht neu generiert werden.");
        }
        boolean hasIncludedContent = java.util.stream.Stream.concat(
                        draft.getSections().stream().map(section -> section.getReviewStatus()),
                        draft.getElements().stream().map(element -> element.getReviewStatus()))
                .anyMatch(status -> status != DraftReviewStatus.REJECTED);
        if (hasIncludedContent) {
            throw new ConflictException("Nur ein vollständig verworfener Entwurf kann neu generiert werden.");
        }
        var project = draft.getProject();
        if (project.getStatus() != ProjectStatus.DRAFT || project.getLocation() != ProjectLocation.DRAFT
                || !project.getSections().isEmpty() || !project.getElements().isEmpty()) {
            throw new ConflictException("Das Projekt wurde bereits aktiviert.");
        }
        var workflow = workflowRepository.findByIdForUpdate(
                        workflowRepository.findByProjectId(projectId)
                                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."))
                                .getId())
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        workflow.prepareDraftRegeneration();
        project.attachDraft(null);
        planDraftRepository.delete(draft);
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflow.getId()));
        return workflow.getId();
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

    private AiPlanGenerationWorkflow requireForUpdate(UUID workflowId) {
        return workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
    }

}

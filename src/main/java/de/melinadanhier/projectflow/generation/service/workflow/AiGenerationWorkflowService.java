package de.melinadanhier.projectflow.generation.service.workflow;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalError;
import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;
import de.melinadanhier.projectflow.ai.model.AiOperation;
import de.melinadanhier.projectflow.ai.model.generation.GeneratedPlanResponse;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckResult;
import de.melinadanhier.projectflow.ai.model.precheck.AiPreCheckSeverity;
import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.mapper.GeneratedPlanDraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.repository.DraftRepository;
import de.melinadanhier.projectflow.draft.service.DraftMaterializationService;
import de.melinadanhier.projectflow.draft.service.DraftVersionConflictException;
import de.melinadanhier.projectflow.generation.event.AiGenerationRequestedEvent;
import de.melinadanhier.projectflow.generation.model.workflow.AiGenerationWork;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.persistence.AiWorkflowPayloadCodec;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiGenerationWorkflowService {
    @Value("${projectflow.ai.max-run-time:5m}")
    private Duration maxRunTime;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final AiWorkflowPayloadCodec payloadCodec;
    private final DraftMaterializationService draftMaterializationService;
    private final GeneratedPlanDraftMapper draftMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectRepository projectRepository;
    private final DraftRepository draftRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public Optional<AiGenerationWork> claimWork(UUID workflowId, UUID runId) {
        if (workflowRepository.claimGeneration(workflowId, runId, Instant.now(clock)) != 1) {
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
                runId,
                payloadCodec.readSnapshot(workflow.getConfirmedSnapshot()),
                result.problems().stream()
                        .filter(problem -> problem.severity() == AiPreCheckSeverity.WARNING)
                        .toList(),
                assumptionContext.confirmedAssumptions(),
                assumptionContext.rejectedAssumptions(),
                workflow.getGenerationRoundAttemptCount()));
    }

    @Transactional
    public void recordProviderCall(UUID workflowId, UUID runId,
                                   String promptVersion, String schemaVersion) {
        AiPlanGenerationWorkflow workflow = requireForUpdate(workflowId);
        if (!workflow.isActiveRun(runId, Instant.now(clock),
                AiPlanGenerationWorkflowStatus.GENERATION_RUNNING)) {
            throw new ConflictException("Die Generierung ist nicht mehr aktiv.");
        }
        workflow.recordGenerationAttempt(promptVersion, schemaVersion);
    }

    @Transactional
    public boolean isActive(UUID workflowId, UUID runId) {
        var workflow = requireForUpdate(workflowId);
        return workflow.isActiveRun(runId, Instant.now(clock),
                AiPlanGenerationWorkflowStatus.GENERATION_RUNNING);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean recordSuccess(UUID workflowId, UUID runId, GeneratedPlanResponse result) {
        var contents = draftMapper.map(result);
        return draftMaterializationService.materialize(
                workflowId, runId, contents, payloadCodec.writeGeneratedPlan(result),
                !result.criticalAssumptions().isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordGenerationFailure(UUID workflowId, UUID runId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = requireForUpdate(workflowId);
        if (!workflow.isActiveRun(runId, Instant.now(clock),
                AiPlanGenerationWorkflowStatus.GENERATION_RUNNING)) {
            return false;
        }
        workflow.recordGenerationFailure(error);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordTechnicalFailure(UUID workflowId, UUID runId, AiTechnicalError error) {
        AiPlanGenerationWorkflow workflow = requireForUpdate(workflowId);
        if (!workflow.isActiveRun(runId, Instant.now(clock),
                AiPlanGenerationWorkflowStatus.GENERATION_RUNNING)) {
            return false;
        }
        workflow.recordTechnicalFailure(error);
        return true;
    }

    @Transactional
    public void retry(UUID workflowId, UUID userId) {
        var workflow = workflowRepository.findOwnedByIdForUpdate(workflowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KI-Workflow wurde nicht gefunden."));
        if ((workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_FAILED
                && workflow.getStatus() != AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE)
                || workflow.getLastAiOperation() != AiOperation.PLAN_GENERATION
                || !Boolean.TRUE.equals(workflow.getLastErrorRetryable())) {
            throw new ConflictException("Die Generierung kann in diesem Zustand nicht erneut gestartet werden.");
        }
        Instant now = Instant.now(clock);
        UUID runId = UUID.randomUUID();
        workflow.startGeneration(runId, now.plus(maxRunTime != null ? maxRunTime : Duration.ofMinutes(5)));
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId, runId));
    }

    @Transactional
    public UUID regenerateDraft(UUID projectId, UUID draftId, UUID userId, long lockVersion) {
        projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt oder Ressource wurde nicht gefunden."));
        authorizationService.requireOwner(projectId, userId);
        var draft = draftRepository.findForUpdateByProjectId(projectId)
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
        project.attachDraft(null);
        draftRepository.delete(draft);
        UUID runId = UUID.randomUUID();
        workflow.startGeneration(runId, Instant.now(clock).plus(
                maxRunTime != null ? maxRunTime : Duration.ofMinutes(5)));
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflow.getId(), runId));
        return workflow.getId();
    }

    /** Technical entry point after repairing the server-side AI client configuration; not exposed in the user UI. */
    @Transactional
    public boolean retryAfterAdministrativeFix(UUID workflowId) {
        var workflow = workflowRepository.findByIdForUpdate(workflowId).orElse(null);
        if (workflow == null
                || workflow.getStatus() != AiPlanGenerationWorkflowStatus.TECHNICAL_FAILURE
                || workflow.getLastTechnicalError() != AiTechnicalErrorCode.CLIENT_CONFIGURATION_ERROR
                || workflow.getLastAiOperation() != AiOperation.PLAN_GENERATION) {
            return false;
        }
        Instant now = Instant.now(clock);
        UUID runId = UUID.randomUUID();
        workflow.startGeneration(runId, now.plus(maxRunTime != null ? maxRunTime : Duration.ofMinutes(5)));
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(workflowId, runId));
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

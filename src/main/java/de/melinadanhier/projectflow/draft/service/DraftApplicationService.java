package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.draft.dto.DraftApplicationSummary;
import de.melinadanhier.projectflow.draft.model.*;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflowStatus;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DraftApplicationService {
    private final PlanDraftRepository planDraftRepository;
    private final ProjectRepository projectRepository;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectStateService projectStateService;
    private final DraftValidationService validationService;
    private final DraftPlanAdoptionFactory adoptionFactory;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DraftApplicationSummary summarize(UUID projectId, UUID userId) {
        authorizationService.requireOwner(projectId, userId);
        requireReleasedDraft(projectId);
        DraftPlan draft = planDraftRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Für dieses Projekt ist kein Planentwurf vorhanden."));
        if (!editable(draft.getStatus())) {
            throw new ConflictException("Der Planentwurf kann in diesem Zustand nicht übernommen werden.");
        }
        return summary(draft);
    }

    /** Compatibility entry point for callers which have not opened the confirmation page yet. */
    @Transactional
    public UUID apply(UUID projectId, UUID userId) {
        authorizationService.requireOwner(projectId, userId);
        var existing = planDraftRepository.findByProjectId(projectId);
        if (existing.isPresent() && existing.get().getStatus() == DraftPlanStatus.APPLIED
                && existing.get().getProject().getStatus() == ProjectStatus.ACTIVE
                && existing.get().getProject().getLocation() == ProjectLocation.OVERVIEW) {
            return projectId;
        }
        DraftApplicationSummary summary = summarize(projectId, userId);
        if (summary.pendingElementCount() > 0) {
            throw new PendingDraftElementsConfirmationRequiredException(legacyReview(summary));
        }
        return confirmAndApply(projectId, summary.draftId(), userId, summary.lockVersion(), false);
    }

    @Transactional
    public UUID confirmAndApply(UUID projectId, UUID userId, long lockVersion) {
        return confirmAndApply(projectId, null, userId, lockVersion, false);
    }

    @Transactional
    public UUID continueWithPending(UUID projectId, UUID userId, long lockVersion) {
        return confirmAndApply(projectId, null, userId, lockVersion, false);
    }

    @Transactional
    public UUID confirmAndApply(UUID projectId, UUID userId, long lockVersion, boolean ignoredIncludePending) {
        return confirmAndApply(projectId, null, userId, lockVersion, false);
    }

    @Transactional
    public UUID confirmAndApply(UUID projectId, UUID draftId, UUID userId,
                                long confirmedVersion, boolean allowEmpty) {
        projectRepository.findForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt oder Ressource wurde nicht gefunden."));
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = planDraftRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Für dieses Projekt ist kein Planentwurf vorhanden."));
        Project project = draft.getProject();
        if (project == null || !projectId.equals(project.getId())) {
            throw new ResourceNotFoundException("Für dieses Projekt ist kein Planentwurf vorhanden.");
        }
        if (draft.getId() == null || draftId != null && !draft.getId().equals(draftId)) {
            if (project.getStatus() != ProjectStatus.DRAFT) {
                throw new DraftVersionConflictException(
                        "Das Projekt wurde bereits mit einem anderen Plan aktiviert.", false);
            }
            throw new ResourceNotFoundException("Der angegebene Planentwurf wurde nicht gefunden.");
        }
        if (draft.getStatus() == DraftPlanStatus.APPLIED) {
            if (project.getStatus() == ProjectStatus.ACTIVE && project.getLocation() == ProjectLocation.OVERVIEW) {
                return projectId;
            }
            throw new ConflictException("Der übernommene Entwurf und der Projektstatus sind inkonsistent.");
        }
        if (project.getStatus() != ProjectStatus.DRAFT || project.getLocation() != ProjectLocation.DRAFT
                || !project.getSections().isEmpty() || !project.getElements().isEmpty()) {
            throw new DraftVersionConflictException(
                    "Das Projekt wurde bereits mit einem anderen Plan aktiviert.", false);
        }
        if (!editable(draft.getStatus())) {
            throw new ConflictException("Der Planentwurf kann in diesem Zustand nicht übernommen werden.");
        }
        if (confirmedVersion != draft.getLockVersion()) {
            throw new DraftVersionConflictException(
                    "Der Entwurf wurde zwischenzeitlich geändert. Bitte prüfe ihn erneut.");
        }
        requireReleasedDraft(projectId);
        DraftApplicationSummary current = summary(draft);
        if (current.empty() && !allowEmpty) {
            throw new DomainValidationException(
                    "Ein vollständig verworfener Entwurf darf nur nach ausdrücklicher Bestätigung als leeres Projekt übernommen werden.");
        }
        validationService.validateForApplication(draft);
        adoptionFactory.adopt(draft, project);
        projectStateService.changeState(project, ProjectStatus.ACTIVE, ProjectLocation.OVERVIEW);
        draft.setStatus(DraftPlanStatus.APPLIED);
        draft.setAppliedAt(Instant.now(clock));
        projectRepository.saveAndFlush(project);
        return projectId;
    }

    private DraftApplicationSummary summary(DraftPlan draft) {
        int pending = (int) Stream.concat(
                        draft.getSections().stream().map(DraftSection::getReviewStatus),
                        draft.getElements().stream().map(DraftPlanElement::getReviewStatus))
                .filter(status -> status == DraftReviewStatus.PENDING).count();
        int includedSections = (int) draft.getSections().stream().filter(this::included).count();
        int includedElements = (int) draft.getElements().stream().filter(this::included).count();
        int omittedDependencies = (int) draft.getElements().stream()
                .filter(DraftTask.class::isInstance).map(DraftTask.class::cast)
                .flatMap(successor -> successor.getPrerequisites().stream()
                        .map(prerequisite -> new Dependency(successor, prerequisite)))
                .filter(dependency -> !included(dependency.successor()) || !included(dependency.prerequisite()))
                .count();
        return new DraftApplicationSummary(draft.getId(), draft.getProject().getId(),
                draft.getProject().getTitle(), draft.getLockVersion(), pending, omittedDependencies,
                includedSections, includedElements);
    }

    private boolean included(DraftSection section) { return included(section.getReviewStatus()); }
    private boolean included(DraftPlanElement element) { return included(element.getReviewStatus()); }
    private boolean included(DraftReviewStatus status) {
        return status == DraftReviewStatus.ACCEPTED || status == DraftReviewStatus.PENDING;
    }
    private boolean editable(DraftPlanStatus status) {
        return status == DraftPlanStatus.READY_FOR_REVIEW || status == DraftPlanStatus.IN_REVIEW;
    }

    private void requireReleasedDraft(UUID projectId) {
        workflowRepository.findByProjectId(projectId).ifPresent(workflow -> {
            if (workflow.getStatus() != AiPlanGenerationWorkflowStatus.GENERATION_COMPLETED) {
                throw new ConflictException("Bitte schließe zuerst die Prüfung der kritischen Annahmen ab.");
            }
        });
    }

    private de.melinadanhier.projectflow.draft.dto.DraftReviewDto legacyReview(DraftApplicationSummary summary) {
        var dto = new de.melinadanhier.projectflow.draft.dto.DraftReviewDto();
        dto.setId(summary.draftId());
        dto.setProjectId(summary.projectId());
        dto.setProjectTitle(summary.projectTitle());
        dto.setLockVersion(summary.lockVersion());
        dto.setTotalElementCount(summary.pendingElementCount());
        return dto;
    }

    private record Dependency(DraftTask successor, DraftTask prerequisite) { }
}

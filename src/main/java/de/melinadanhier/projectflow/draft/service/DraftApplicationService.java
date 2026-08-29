package de.melinadanhier.projectflow.draft.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.draft.model.DraftMilestone;
import de.melinadanhier.projectflow.draft.mapper.DraftMapper;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.model.DraftPlanElement;
import de.melinadanhier.projectflow.draft.model.DraftPlanStatus;
import de.melinadanhier.projectflow.draft.model.DraftReviewStatus;
import de.melinadanhier.projectflow.draft.model.DraftSection;
import de.melinadanhier.projectflow.draft.model.DraftTask;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.generation.model.workflow.AiPlanGenerationWorkflow;
import de.melinadanhier.projectflow.generation.repository.AiPlanGenerationWorkflowRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectStateService;
import de.melinadanhier.projectflow.planelement.model.ElementOrigin;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.model.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DraftApplicationService {

    private final PlanDraftRepository planDraftRepository;
    private final AiPlanGenerationWorkflowRepository workflowRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectStateService projectStateService;
    private final DraftValidationService validationService;
    private final DraftMapper draftMapper;

    @Transactional
    public UUID apply(UUID projectId, UUID userId) {
        return apply(projectId, userId, null);
    }

    @Transactional
    public UUID confirmAndApply(UUID projectId, UUID userId, long lockVersion) {
        return apply(projectId, userId, lockVersion);
    }

    private UUID apply(UUID projectId, UUID userId, Long confirmedVersion) {
        authorizationService.requireOwner(projectId, userId);
        DraftPlan draft = planDraftRepository.findForUpdateByProjectId(projectId)
                .orElseThrow(() -> new ConflictException(
                        "Für dieses Projekt ist kein Planentwurf vorhanden."));
        UUID draftId = draft.getId();
        Project project = draft.getProject();
        if (draftId == null || project == null || !projectId.equals(project.getId())) {
            throw new ConflictException("Der Planentwurf gehört nicht zu diesem Projekt.");
        }

        if (draft.getStatus() == DraftPlanStatus.APPLIED) {
            return projectId;
        }
        if (draft.getStatus() != DraftPlanStatus.READY_FOR_REVIEW
                && draft.getStatus() != DraftPlanStatus.IN_REVIEW) {
            throw new ConflictException("Der Planentwurf kann in diesem Zustand nicht übernommen werden.");
        }
        if (!project.getSections().isEmpty() || !project.getElements().isEmpty()) {
            throw new ConflictException("Der aktive Projektplan enthält bereits Inhalte.");
        }

        if (confirmedVersion != null && confirmedVersion != draft.getLockVersion()) {
            throw new ConflictException("Der Entwurf wurde zwischenzeitlich geändert. Bitte prüfe ihn erneut.");
        }
        var review = draftMapper.toReviewDto(draft);
        if (confirmedVersion == null && !review.getUncheckedCriticalTasks().isEmpty()) {
            throw new CriticalAssumptionsConfirmationRequiredException(review);
        }
        validationService.validate(draft);

        project.setSortMode(draft.getSortMode());

        draft.setStatus(DraftPlanStatus.APPLYING);
        Map<DraftSection, PlanSection> sections = new HashMap<>();
        draft.getSections().forEach(source -> {
            PlanSection target = new PlanSection();
            target.setTitle(source.getTitle());
            target.setDescription(source.getDescription());
            target.setSortOrder(source.getSortOrder());
            target.setOrigin(source.getOrigin());
            project.addSection(target);
            sections.put(source, target);
            source.setReviewStatus(DraftReviewStatus.ACCEPTED);
        });
        draft.getElements().forEach(source -> {
            PlanElement target = copy(source);
            project.addElement(target);
            if (source.getDraftSection() != null) {
                sections.get(source.getDraftSection()).addElement(target);
            }
            source.setReviewStatus(DraftReviewStatus.ACCEPTED);
        });

        projectStateService.changeState(project, ProjectStatus.ACTIVE, ProjectLocation.OVERVIEW);
        draft.setStatus(DraftPlanStatus.APPLIED);
        workflowRepository.findByProjectId(projectId)
                .ifPresent(AiPlanGenerationWorkflow::markDraftApplied);
        return projectId;
    }

    private PlanElement copy(DraftPlanElement source) {
        PlanElement target;
        if (source instanceof DraftTask draftTask) {
            Task task = new Task();
            task.setPriority(draftTask.getPriority());
            task.setStartDate(draftTask.getStartDate());
            task.setDueDate(draftTask.getDueDate());
            task.setEstimatedHours(draftTask.getEstimatedHours());
            target = task;
        } else if (source instanceof DraftMilestone draftMilestone) {
            Milestone milestone = new Milestone();
            milestone.setDueDate(draftMilestone.getDueDate());
            target = milestone;
        } else {
            throw new IllegalStateException("Nicht unterstütztes Entwurfselement.");
        }
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSortOrder(source.getSortOrder());
        target.setOrigin(source.getOrigin());
        return target;
    }
}

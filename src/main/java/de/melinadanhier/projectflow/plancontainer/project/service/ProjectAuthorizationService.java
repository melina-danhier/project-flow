package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.ProjectNotEditableException;
import de.melinadanhier.projectflow.draft.model.DraftPlan;
import de.melinadanhier.projectflow.draft.repository.PlanDraftRepository;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.planelement.model.PlanElement;
import de.melinadanhier.projectflow.planelement.model.PlanSection;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.PlanSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {

    private final ProjectMemberRepository projectMemberRepository;
    private final PlanDraftRepository planDraftRepository;
    private final PlanSectionRepository planSectionRepository;
    private final PlanElementRepository planElementRepository;

    @Transactional(readOnly = true)
    public ProjectMember requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserIdAndActiveTrue(projectId, userId)
                .orElseThrow(this::notAccessible);
    }

    @Transactional(readOnly = true)
    public ProjectMember requireOwner(UUID projectId, UUID userId) {
        ProjectMember membership = requireMember(projectId, userId);
        if (membership.getRole() != ProjectMemberRole.OWNER) {
            throw new ForbiddenOperationException("Für diese Aktion ist die Projekteigentümerschaft erforderlich.");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public ProjectMember requireEditableMember(UUID projectId, UUID userId) {
        ProjectMember membership = requireMember(projectId, userId);
        requireEditable(membership);
        return membership;
    }

    @Transactional(readOnly = true)
    public ProjectMember requireEditableOwner(UUID projectId, UUID userId) {
        ProjectMember membership = requireOwner(projectId, userId);
        requireEditable(membership);
        return membership;
    }

    public boolean isEditable(ProjectMember membership) {
        return membership.getProject().getStatus() == ProjectStatus.ACTIVE
                && membership.getProject().getLocation() == ProjectLocation.OVERVIEW;
    }

    private void requireEditable(ProjectMember membership) {
        if (!isEditable(membership)) {
            throw new ProjectNotEditableException(
                    "Nur aktive Projekte in der Projektübersicht können bearbeitet werden."
            );
        }
    }

    @Transactional(readOnly = true)
    public DraftPlan requireDraftOwner(UUID draftId, UUID userId) {
        DraftPlan draft = planDraftRepository.findById(draftId).orElseThrow(this::notAccessible);
        requireOwner(draft.getProject().getId(), userId);
        return draft;
    }

    @Transactional(readOnly = true)
    public DraftPlan requireDraftOwner(UUID projectId, UUID draftId, UUID userId) {
        DraftPlan draft = planDraftRepository.findByIdAndProjectId(draftId, projectId)
                .orElseThrow(this::notAccessible);
        requireOwner(projectId, userId);
        return draft;
    }

    @Transactional(readOnly = true)
    public PlanSection requireSection(UUID projectId, UUID sectionId, UUID userId) {
        requireMember(projectId, userId);
        return planSectionRepository.findByIdAndPlanContainerId(sectionId, projectId)
                .orElseThrow(this::notAccessible);
    }

    @Transactional(readOnly = true)
    public PlanElement requirePlanElement(UUID projectId, UUID elementId, UUID userId) {
        requireMember(projectId, userId);
        return planElementRepository.findByIdAndPlanContainerId(elementId, projectId)
                .orElseThrow(this::notAccessible);
    }

    private ResourceNotFoundException notAccessible() {
        return new ResourceNotFoundException("Projekt oder Ressource wurde nicht gefunden.");
    }
}

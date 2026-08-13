package com.melina.projectflow.plancontainer.project.service;

import com.melina.projectflow.common.exception.ForbiddenOperationException;
import com.melina.projectflow.common.exception.ResourceNotFoundException;
import com.melina.projectflow.generation.model.PlanDraft;
import com.melina.projectflow.generation.repository.PlanDraftRepository;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import com.melina.projectflow.plancontainer.project.model.ProjectMemberRole;
import com.melina.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import com.melina.projectflow.planelement.model.PlanElement;
import com.melina.projectflow.planelement.model.PlanSection;
import com.melina.projectflow.planelement.repository.PlanElementRepository;
import com.melina.projectflow.planelement.repository.PlanSectionRepository;
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
    public PlanDraft requireDraftOwner(UUID draftId, UUID userId) {
        PlanDraft draft = planDraftRepository.findById(draftId).orElseThrow(this::notAccessible);
        requireOwner(draft.getProject().getId(), userId);
        return draft;
    }

    @Transactional(readOnly = true)
    public PlanDraft requireDraftOwner(UUID projectId, UUID draftId, UUID userId) {
        PlanDraft draft = planDraftRepository.findByIdAndProjectId(draftId, projectId)
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

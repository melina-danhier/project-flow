package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.planelement.mapper.PlanElementMapper;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMemberRole;
import de.melinadanhier.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.PlanElementRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanElementService {

    private final PlanElementRepository planElementRepository;
    private final PlanElementMapper planElementMapper;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public void assignTask(UUID projectId, UUID taskId, UUID assigneeUserId, UUID actingUserId) {
        var project = authorizationService.requireEditableMemberForUpdate(projectId, actingUserId).getProject();
        if (!project.isGroupProject()) {
            throw new DomainValidationException(
                    "Aufgabenzuständigkeiten sind nur bei Gruppenprojekten möglich.");
        }
        lockMembershipChanges(projectId);
        authorizationService.requireEditableMember(projectId, actingUserId);
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        ProjectMember assignee = projectMemberRepository
                .findByProjectIdAndUserIdAndActiveTrue(projectId, assigneeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Aktives Projektmitglied wurde nicht gefunden."));
        task.setAssignee(assignee);
    }

    @Transactional
    public void unassignTask(UUID projectId, UUID taskId, UUID actingUserId) {
        var project = authorizationService.requireEditableMemberForUpdate(projectId, actingUserId).getProject();
        if (!project.isGroupProject()) {
            throw new DomainValidationException(
                    "Aufgabenzuständigkeiten sind nur bei Gruppenprojekten möglich.");
        }
        lockMembershipChanges(projectId);
        authorizationService.requireEditableMember(projectId, actingUserId);
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        task.setAssignee(null);
    }

    private void lockMembershipChanges(UUID projectId) {
        projectMemberRepository.findActiveOwnerForUpdate(projectId, ProjectMemberRole.OWNER)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
    }
}

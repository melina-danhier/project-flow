package com.melina.projectflow.planelement.service;

import com.melina.projectflow.planelement.mapper.PlanElementMapper;
import com.melina.projectflow.common.exception.ResourceNotFoundException;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import com.melina.projectflow.plancontainer.project.model.ProjectMemberRole;
import com.melina.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import com.melina.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import com.melina.projectflow.planelement.model.Task;
import com.melina.projectflow.planelement.repository.PlanElementRepository;
import com.melina.projectflow.planelement.repository.TaskRepository;
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
        authorizationService.requireMember(projectId, actingUserId);
        lockMembershipChanges(projectId);
        authorizationService.requireMember(projectId, actingUserId);
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        ProjectMember assignee = projectMemberRepository
                .findByProjectIdAndUserIdAndActiveTrue(projectId, assigneeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Aktives Projektmitglied wurde nicht gefunden."));
        task.setAssignee(assignee);
    }

    @Transactional
    public void unassignTask(UUID projectId, UUID taskId, UUID actingUserId) {
        authorizationService.requireMember(projectId, actingUserId);
        lockMembershipChanges(projectId);
        authorizationService.requireMember(projectId, actingUserId);
        Task task = taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
        task.setAssignee(null);
    }

    private void lockMembershipChanges(UUID projectId) {
        projectMemberRepository.findActiveOwnerForUpdate(projectId, ProjectMemberRole.OWNER)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt wurde nicht gefunden."));
    }
}

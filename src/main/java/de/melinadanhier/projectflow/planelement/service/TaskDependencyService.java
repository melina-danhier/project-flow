package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyDto;
import de.melinadanhier.projectflow.planelement.dto.TaskDependencyForm;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskRepository taskRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional
    public TaskDependencyDto createDependency(UUID projectId, TaskDependencyForm form, UUID userId) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        Task prerequisite = requireTask(projectId, form.getPrerequisiteTaskId());
        Task successor = requireTask(projectId, form.getSuccessorTaskId());
        if (prerequisite.getId().equals(successor.getId())) {
            throw new DomainValidationException("Eine Aufgabe darf nicht von sich selbst abhängen.");
        }
        if (successor.getPrerequisites().stream().anyMatch(task -> task.getId().equals(prerequisite.getId()))) {
            throw new ConflictException("Diese Aufgabenabhängigkeit besteht bereits.");
        }
        if (dependsOn(prerequisite, successor.getId(), new HashSet<>())) {
            throw new DomainValidationException("Diese Aufgabenabhängigkeit würde einen Zyklus erzeugen.");
        }
        successor.addPrerequisite(prerequisite);
        try {
            taskRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Diese Aufgabenabhängigkeit besteht bereits.");
        }
        return new TaskDependencyDto(
                prerequisite.getId(), prerequisite.getTitle(), successor.getId(), successor.getTitle());
    }

    @Transactional
    public void deleteDependency(
            UUID projectId,
            UUID successorTaskId,
            UUID prerequisiteTaskId,
            UUID userId
    ) {
        authorizationService.requireEditableMemberForUpdate(projectId, userId);
        Task successor = requireTask(projectId, successorTaskId);
        Task prerequisite = requireTask(projectId, prerequisiteTaskId);
        boolean removed = successor.getPrerequisites().removeIf(
                task -> task.getId().equals(prerequisite.getId()));
        if (!removed) {
            throw new ResourceNotFoundException("Aufgabenabhängigkeit wurde nicht gefunden.");
        }
    }

    private Task requireTask(UUID projectId, UUID taskId) {
        return taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
    }

    private boolean dependsOn(Task task, UUID possiblePrerequisiteId, Set<UUID> visited) {
        if (!visited.add(task.getId())) {
            return false;
        }
        return task.getPrerequisites().stream().anyMatch(prerequisite ->
                prerequisite.getId().equals(possiblePrerequisiteId)
                        || dependsOn(prerequisite, possiblePrerequisiteId, visited));
    }
}

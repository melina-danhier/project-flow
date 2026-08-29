package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.planelement.dto.TaskCommentDto;
import de.melinadanhier.projectflow.planelement.dto.TaskCommentForm;
import de.melinadanhier.projectflow.planelement.dto.TaskCommentSectionDto;
import de.melinadanhier.projectflow.planelement.model.Task;
import de.melinadanhier.projectflow.planelement.model.TaskComment;
import de.melinadanhier.projectflow.planelement.repository.TaskCommentRepository;
import de.melinadanhier.projectflow.planelement.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskCommentRepository taskCommentRepository;
    private final TaskRepository taskRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public TaskCommentSectionDto getCommentSection(UUID projectId, UUID taskId, UUID userId) {
        ProjectMember membership = authorizationService.requireMember(projectId, userId);
        requireTask(projectId, taskId);
        boolean projectEditable = authorizationService.isEditable(membership);
        List<TaskCommentDto> comments = taskCommentRepository.findAllForTask(projectId, taskId).stream()
                .map(comment -> toDto(comment,
                        projectEditable && comment.getAuthor().getId().equals(membership.getId())))
                .toList();
        return new TaskCommentSectionDto(
                comments,
                membership.getProject().getCollaborationMode() == CollaborationMode.GROUP
        );
    }

    @Transactional
    public TaskCommentDto addComment(UUID projectId, UUID taskId, TaskCommentForm form, UUID userId) {
        ProjectMember membership = authorizationService.requireEditableMember(projectId, userId);
        Task task = requireTask(projectId, taskId);
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(membership);
        comment.setContent(form.getContent().trim());
        return toDto(taskCommentRepository.save(comment), true);
    }

    @Transactional
    public void deleteOwnComment(UUID projectId, UUID taskId, UUID commentId, UUID userId) {
        ProjectMember membership = authorizationService.requireEditableMember(projectId, userId);
        TaskComment comment = taskCommentRepository.findForTask(projectId, taskId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Beitrag wurde nicht gefunden."));
        if (!comment.getAuthor().getId().equals(membership.getId())) {
            throw new ForbiddenOperationException("Du kannst nur eigene Beiträge löschen.");
        }
        taskCommentRepository.delete(comment);
    }

    private Task requireTask(UUID projectId, UUID taskId) {
        return taskRepository.findByIdAndPlanContainerId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Aufgabe wurde nicht gefunden."));
    }

    private TaskCommentDto toDto(TaskComment comment, boolean deletable) {
        return new TaskCommentDto(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getUser().getDisplayName(),
                comment.getCreatedAt(),
                deletable
        );
    }
}

package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.ConflictException;
import de.melinadanhier.projectflow.common.exception.ForbiddenOperationException;
import de.melinadanhier.projectflow.common.exception.ResourceNotFoundException;
import de.melinadanhier.projectflow.plancontainer.project.model.membership.ProjectMember;
import de.melinadanhier.projectflow.plancontainer.project.service.ProjectAuthorizationService;
import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;
import de.melinadanhier.projectflow.planelement.dto.MilestoneCommentDto;
import de.melinadanhier.projectflow.planelement.dto.MilestoneCommentForm;
import de.melinadanhier.projectflow.planelement.dto.MilestoneCommentSectionDto;
import de.melinadanhier.projectflow.planelement.model.Milestone;
import de.melinadanhier.projectflow.planelement.model.MilestoneComment;
import de.melinadanhier.projectflow.planelement.repository.MilestoneCommentRepository;
import de.melinadanhier.projectflow.planelement.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneCommentService {

    private final MilestoneCommentRepository milestoneCommentRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectAuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public MilestoneCommentSectionDto getCommentSection(UUID projectId, UUID milestoneId, UUID userId) {
        ProjectMember membership = authorizationService.requireMember(projectId, userId);
        requireMilestone(projectId, milestoneId);
        boolean projectEditable = authorizationService.isEditable(membership);
        List<MilestoneCommentDto> comments = milestoneCommentRepository.findAllForMilestone(projectId, milestoneId).stream()
                .map(comment -> toDto(comment,
                        projectEditable && comment.getAuthor().getId().equals(membership.getId())))
                .toList();
        return new MilestoneCommentSectionDto(
                comments,
                membership.getProject().getCollaborationMode() == CollaborationMode.GROUP
        );
    }

    @Transactional
    public MilestoneCommentDto addComment(UUID projectId, UUID milestoneId, MilestoneCommentForm form, UUID userId) {
        ProjectMember membership = authorizationService.requireEditableMember(projectId, userId);
        Milestone milestone = requireMilestone(projectId, milestoneId);
        MilestoneComment comment = new MilestoneComment();
        comment.setMilestone(milestone);
        comment.setAuthor(membership);
        comment.setContent(form.getContent().trim());
        return toDto(milestoneCommentRepository.save(comment), true);
    }

    @Transactional
    public MilestoneCommentDto updateOwnComment(UUID projectId, UUID milestoneId, UUID commentId,
                                           MilestoneCommentForm form, UUID userId) {
        ProjectMember membership = authorizationService.requireEditableMember(projectId, userId);
        MilestoneComment comment = milestoneCommentRepository.findForMilestone(projectId, milestoneId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Beitrag wurde nicht gefunden."));
        if (!comment.getAuthor().getId().equals(membership.getId())) {
            throw new ForbiddenOperationException("Du kannst nur eigene Beiträge bearbeiten.");
        }
        if (form.getLockVersion() == null || comment.getLockVersion() != form.getLockVersion()) {
            throw new ConflictException(
                    "Der Beitrag wurde zwischenzeitlich geändert. Bitte lade die Seite neu.");
        }
        comment.setContent(form.getContent().trim());
        return toDto(comment, true);
    }

    @Transactional
    public void deleteOwnComment(UUID projectId, UUID milestoneId, UUID commentId, UUID userId) {
        ProjectMember membership = authorizationService.requireEditableMember(projectId, userId);
        MilestoneComment comment = milestoneCommentRepository.findForMilestone(projectId, milestoneId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Beitrag wurde nicht gefunden."));
        if (!comment.getAuthor().getId().equals(membership.getId())) {
            throw new ForbiddenOperationException("Du kannst nur eigene Beiträge löschen.");
        }
        milestoneCommentRepository.delete(comment);
    }

    private Milestone requireMilestone(UUID projectId, UUID milestoneId) {
        return milestoneRepository.findByIdAndPlanContainerId(milestoneId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Meilenstein wurde nicht gefunden."));
    }

    private MilestoneCommentDto toDto(MilestoneComment comment, boolean deletable) {
        return new MilestoneCommentDto(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getUser().getDisplayName(),
                comment.getCreatedAt(),
                comment.getLockVersion(),
                deletable
        );
    }
}
